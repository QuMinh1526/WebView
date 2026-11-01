(() => {
  "use strict";

  const MAPPING = __VOICEFX_MAPPING__;
  const mapScale = __VOICEFX_SCALE__;
  const INITIAL = __VOICEFX_INITIAL__;
  const BLOCKS = [
    "noiseGate", "highPass", "lowPass", "eq", "compressor", "pitch",
    "distortion", "bitcrusher", "chorus", "robot", "echo", "reverb"
  ];
  const origin = window.location.origin;
  const host = window.location.hostname.toLowerCase();
  const verificationFrame = host === "challenges.cloudflare.com" ||
    host === "hcaptcha.com" || host.endsWith(".hcaptcha.com") ||
    host === "recaptcha.net" || host.endsWith(".recaptcha.net") ||
    ((host === "www.google.com" || host === "www.gstatic.com") &&
      window.location.pathname.startsWith("/recaptcha"));
  if (window.top !== window || !INITIAL.origins.includes(origin) ||
      verificationFrame) return;
  if (window.__voicefx) return;

  const mediaDevices = navigator.mediaDevices;
  if (!mediaDevices || typeof mediaDevices.getUserMedia !== "function") return;
  const originalGetUserMedia = mediaDevices.getUserMedia.bind(mediaDevices);
  const originalEnumerateDevices =
    typeof mediaDevices.enumerateDevices === "function"
      ? mediaDevices.enumerateDevices.bind(mediaDevices)
      : null;
  function reportMicrophoneError(error, preferredCode) {
    const name = error && error.name;
    const code = preferredCode ||
      (!window.isSecureContext ? "Insecure" :
        active?.context?.state === "suspended" ? "AudioContextSuspended" :
          name === "NotAllowedError" || name === "PermissionDeniedError" ? "NotAllowed" :
            name === "NotFoundError" || name === "DevicesNotFoundError" ? "NotFound" :
              name === "NotReadableError" || name === "AbortError" ? "NotReadable" : null);
    if (code) {
      try {
        window.__voiceFxBridge?.postMessage(JSON.stringify({
          type: "microphoneError", code
        }));
      } catch (_) {}
    }
  }
  const DEFAULT_PARAMETERS = {
    outputGain: 100, noiseGateDb: 0, highPassHz: 0, lowPassHz: 100,
    eqLowDb: 50, eq300Db: 50, eq1kDb: 50, eq35kDb: 50, eqHighDb: 50,
    compressorRatio: 0, pitchSemitones: 50, formantSemitones: 50,
    distortion: 0, bitDepth: 100, chorusMix: 0, robotMix: 0,
    echoMs: 0, echoMix: 0, reverbMix: 0, reverbRoom: 50, reverbDecay: 0
  };
  let state = {
    enabled: INITIAL.enabled === true,
    hudVisible: INITIAL.hudVisible === true,
    parameters: { ...DEFAULT_PARAMETERS, ...INITIAL.parameters },
    blocks: new Set(INITIAL.blocks.filter(name => BLOCKS.includes(name))),
    mode: "normal"
  };
  let active = null;
  let lastStats = {};
  let hudTimer = 0;

  const clamp = (value, min, max) => Math.min(max, Math.max(min, Number(value) || 0));
  const value = name => mapScale(state.parameters[name] ?? 0, false, MAPPING[name]);
  const blockOn = name => state.enabled && state.blocks.has(name);

  const workletSource = `
    class GranularPitchProcessor extends AudioWorkletProcessor {
      constructor() {
        super();
        this.size = 8192;
        this.buffer = new Float32Array(this.size);
        this.write = 0;
        this.phase = [0, 0.5];
        this.starts = [0, 0];
        this.ratio = 1;
        this.port.onmessage = event => {
          this.ratio = Math.pow(2, Math.max(-12, Math.min(12, event.data.semitones || 0)) / 12);
        };
      }
      process(inputs, outputs) {
        const input = inputs[0] && inputs[0][0];
        const output = outputs[0] && outputs[0][0];
        if (!output) return true;
        if (!input) { output.fill(0); return true; }
        const grain = 2048;
        for (let i = 0; i < output.length; i++) {
          const sample = input[i] || 0;
          this.buffer[this.write] = sample;
          let sum = 0, weights = 0;
          for (let g = 0; g < 2; g++) {
            let phase = this.phase[g];
            if (phase === 0) this.starts[g] = (this.write - Math.ceil(grain * Math.max(1, this.ratio)) + this.size) % this.size;
            const read = (this.starts[g] + Math.floor(phase * grain * this.ratio)) % this.size;
            const window = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
            sum += this.buffer[read] * window;
            weights += window;
            phase += 1 / grain;
            if (phase >= 1) phase = 0;
            this.phase[g] = phase;
          }
          output[i] = weights > 0.001 ? sum / weights : sample;
          this.write = (this.write + 1) % this.size;
        }
        return true;
      }
    }
    class BitCrusherProcessor extends AudioWorkletProcessor {
      constructor() {
        super();
        this.bits = 16;
        this.port.onmessage = event => { this.bits = Math.max(4, Math.min(16, event.data.bits || 16)); };
      }
      process(inputs, outputs) {
        const input = inputs[0] && inputs[0][0], output = outputs[0] && outputs[0][0];
        if (!output) return true;
        if (!input) { output.fill(0); return true; }
        const steps = Math.pow(2, this.bits - 1);
        for (let i = 0; i < output.length; i++) output[i] = Math.round(input[i] * steps) / steps;
        return true;
      }
    }
    registerProcessor("voicefx-pitch", GranularPitchProcessor);
    registerProcessor("voicefx-crush", BitCrusherProcessor);
  `;

  function makeShaperCurve(amount) {
    const curve = new Float32Array(2048);
    const drive = 1 + amount * 10;
    for (let i = 0; i < curve.length; i++) {
      const x = (i * 2) / (curve.length - 1) - 1;
      curve[i] = Math.tanh(x * drive);
    }
    return curve;
  }

  function makeReverb(context) {
    const convolver = context.createConvolver();
    const regenerate = () => {
      const seconds = clamp(value("reverbDecay"), 0.2, 3);
      const frames = Math.min(context.sampleRate * seconds, context.sampleRate * 3);
      const impulse = context.createBuffer(2, frames, context.sampleRate);
      const room = clamp(value("reverbRoom"), 0.1, 0.8);
      for (let channel = 0; channel < 2; channel++) {
        const data = impulse.getChannelData(channel);
        for (let i = 0; i < frames; i++) {
          const decay = Math.pow(1 - i / frames, 2 + room * 6);
          data[i] = (Math.random() * 2 - 1) * decay;
        }
      }
      convolver.buffer = impulse;
    };
    const input = context.createGain(), dry = context.createGain();
    const wet = context.createGain(), output = context.createGain();
    input.connect(dry).connect(output);
    input.connect(convolver).connect(wet).connect(output);
    dry.gain.value = 1 - value("reverbMix");
    wet.gain.value = value("reverbMix");
    return { input, output, nodes: [input, dry, convolver, wet, output], regenerate };
  }

  function makeEcho(context) {
    const input = context.createGain(), dry = context.createGain();
    const wet = context.createGain(), delay = context.createDelay(1.1);
    const feedback = context.createGain(), output = context.createGain();
    const mix = clamp(value("echoMix"), 0, 0.35);
    delay.delayTime.value = clamp(value("echoMs") / 1000, 0.05, 0.75);
    feedback.gain.value = Math.min(0.65, mix * 1.8);
    dry.gain.value = 1 - mix;
    wet.gain.value = mix;
    input.connect(dry).connect(output);
    input.connect(delay).connect(wet).connect(output);
    delay.connect(feedback).connect(delay);
    return { input, output, nodes: [input, dry, wet, delay, feedback, output] };
  }

  function createGraph(stream) {
    const context = new AudioContext({ latencyHint: "interactive" });
    const source = context.createMediaStreamSource(stream);
    const destination = context.createMediaStreamDestination();
    const gate = context.createDynamicsCompressor();
    gate.knee.value = 0; gate.ratio.value = 8; gate.attack.value = 0.005;
    const highPass = context.createBiquadFilter(); highPass.type = "highpass";
    const lowPass = context.createBiquadFilter(); lowPass.type = "lowpass";
    const eq = [100, 300, 1000, 3500, 8000].map((frequency, index) => {
      const filter = context.createBiquadFilter();
      filter.type = index === 0 || index === 4 ? "lowshelf" : "peaking";
      filter.frequency.value = frequency; filter.Q.value = 0.8;
      return filter;
    });
    const compressor = context.createDynamicsCompressor();
    const pitchGain = context.createGain(), pitchOut = context.createGain();
    const pitchFormant = context.createBiquadFilter();
    pitchFormant.type = "peaking"; pitchFormant.Q.value = 1;
    const distortionIn = context.createGain(), distortionOut = context.createGain();
    const distortion = context.createWaveShaper(); distortion.oversample = "2x";
    const chorusDelay = context.createDelay(0.05), chorusOut = context.createGain();
    const chorusDepth = context.createGain();
    chorusDelay.delayTime.value = 0.02;
    const robotIn = context.createGain(), robotOut = context.createGain();
    const robotDepth = context.createGain();
    const echo = makeEcho(context), reverb = makeReverb(context);
    const output = context.createGain();
    const limiter = context.createDynamicsCompressor();
    limiter.threshold.value = -12;
    limiter.knee.value = 0;
    limiter.ratio.value = 20;
    limiter.attack.value = 0;
    limiter.release.value = 0.08;
    const ceiling = context.createWaveShaper();
    const ceilingCurve = new Float32Array(2048);
    for (let i = 0; i < ceilingCurve.length; i++) {
      ceilingCurve[i] = clamp((i * 2) / (ceilingCurve.length - 1) - 1, -0.25, 0.25);
    }
    ceiling.curve = ceilingCurve;

    let pitchNode = null, crusherNode = null;
    let chorusLfo = null, robotOscillator = null;
    let reverbTimer = 0;
    let meterNodes = null;
    const stages = {
      noiseGate: [gate, gate],
      highPass: [highPass, highPass],
      lowPass: [lowPass, lowPass],
      eq: [eq[0], eq[4]],
      compressor: [compressor, compressor],
      pitch: [pitchGain, pitchOut],
      distortion: [distortionIn, distortionOut],
      bitcrusher: [null, null],
      chorus: [chorusDelay, chorusOut],
      robot: [robotIn, robotOut],
      echo: [echo.input, echo.output],
      reverb: [reverb.input, reverb.output]
    };

    function connectActive() {
      [source, output, limiter, ceiling, destination, gate, highPass, lowPass, compressor,
        ...eq, pitchGain, pitchOut, pitchFormant, pitchNode, crusherNode, distortionIn,
        distortion, distortionOut, chorusDelay, chorusOut, chorusDepth, chorusLfo,
        robotIn, robotOut, robotDepth, robotOscillator,
        ...echo.nodes, ...reverb.nodes].filter(Boolean).forEach(node => node.disconnect());
      if (chorusLfo) { chorusLfo.stop(); chorusLfo = null; }
      if (robotOscillator) { robotOscillator.stop(); robotOscillator = null; }

      let previous = source;
      for (const name of BLOCKS) {
        if (!blockOn(name)) continue;
        const [input, stageOutput] = stages[name];
        if (!input || !stageOutput) continue;
        if (name === "eq") eq.slice(1).forEach((filter, index) => eq[index].connect(filter));
        if (name === "pitch") {
          pitchGain.connect(pitchNode || pitchFormant);
          if (pitchNode) pitchNode.connect(pitchFormant);
          pitchFormant.connect(pitchOut);
        }
        if (name === "distortion") distortionIn.connect(distortion).connect(distortionOut);
        if (name === "bitcrusher") {
          if (!crusherNode) continue;
          stages.bitcrusher = [crusherNode, crusherNode];
        }
        if (name === "chorus") {
          chorusLfo = context.createOscillator();
          chorusLfo.frequency.value = 0.25;
          chorusLfo.connect(chorusDepth).connect(chorusDelay.delayTime);
          chorusLfo.start();
          chorusDelay.connect(chorusOut);
        }
        if (name === "robot") {
          robotOscillator = context.createOscillator();
          robotOscillator.frequency.value = 45;
          robotOscillator.connect(robotDepth).connect(robotIn.gain);
          robotOscillator.start();
          robotIn.connect(robotOut);
        }
        if (name === "echo") {
          echo.input.connect(echo.nodes[1]).connect(echo.output);
          echo.input.connect(echo.nodes[3]).connect(echo.nodes[2]).connect(echo.output);
          echo.nodes[3].connect(echo.nodes[4]).connect(echo.nodes[3]);
        }
        if (name === "reverb") {
          reverb.input.connect(reverb.nodes[1]).connect(reverb.output);
          reverb.input.connect(reverb.nodes[2]).connect(reverb.nodes[3]).connect(reverb.output);
        }
        previous.connect(input);
        previous = stageOutput;
      }
      previous.connect(output).connect(limiter).connect(ceiling).connect(destination);
    }

    function updateParameters() {
      gate.threshold.value = value("noiseGateDb");
      highPass.frequency.value = value("highPassHz");
      lowPass.frequency.value = value("lowPassHz");
      [value("eqLowDb"), value("eq300Db"), value("eq1kDb"), value("eq35kDb"), value("eqHighDb")]
        .forEach((gain, index) => { eq[index].gain.value = gain; });
      compressor.ratio.value = value("compressorRatio");
      compressor.threshold.value = -18;
      compressor.attack.value = 0.003;
      output.gain.value = Math.min(1, value("outputGain"));
      pitchFormant.frequency.value = clamp(1000 * Math.pow(2, value("formantSemitones") / 12), 500, 2000);
      if (pitchNode) pitchNode.port.postMessage({ semitones: value("pitchSemitones") });
      if (crusherNode) crusherNode.port.postMessage({ bits: value("bitDepth") });
      if (blockOn("distortion")) distortion.curve = makeShaperCurve(value("distortion"));
      chorusDepth.gain.value = value("chorusMix") * 0.012;
      chorusOut.gain.value = 1;
      robotDepth.gain.value = value("robotMix");
      robotOut.gain.value = 1;
      echo.nodes[1].gain.value = 1 - value("echoMix");
      echo.nodes[2].gain.value = value("echoMix");
      echo.nodes[4].gain.value = Math.min(0.65, value("echoMix") * 1.8);
      echo.nodes[3].delayTime.value = clamp(value("echoMs") / 1000, 0.05, 1);
      reverb.nodes[1].gain.value = 1 - value("reverbMix");
      reverb.nodes[3].gain.value = value("reverbMix");
      clearTimeout(reverbTimer);
      if (blockOn("reverb")) reverbTimer = setTimeout(reverb.regenerate, 150);
    }

    return {
      context, source, destination, stream, stages, connectActive, updateParameters,
      get pitchNode() { return pitchNode; },
      set pitchNode(node) { pitchNode = node; stages.pitch[0] = pitchGain; },
      get crusherNode() { return crusherNode; },
      set crusherNode(node) { crusherNode = node; stages.bitcrusher = [node, node]; },
      compressor,
      limiter,
      get meterNodes() { return meterNodes; },
      set meterNodes(nodes) { meterNodes = nodes; },
      clearReverbTimer() { clearTimeout(reverbTimer); }
    };
  }

  async function activate(stream) {
    const tracks = stream.getAudioTracks();
    if (!state.enabled || tracks.length === 0) return stream;
    if (active && active.stream !== stream) return stream;
    if (!active) {
      active = createGraph(stream);
      try {
        const moduleUrl = URL.createObjectURL(new Blob([workletSource], { type: "text/javascript" }));
        await active.context.audioWorklet.addModule(moduleUrl);
        URL.revokeObjectURL(moduleUrl);
        active.pitchNode = new AudioWorkletNode(active.context, "voicefx-pitch");
        active.crusherNode = new AudioWorkletNode(active.context, "voicefx-crush");
      } catch (_) {
        active.pitchNode = null;
        active.crusherNode = null;
      }
      active.updateParameters();
      active.connectActive();
    }
    await active.context.resume();
    const processed = new MediaStream([
      ...active.destination.stream.getAudioTracks(),
      ...stream.getVideoTracks()
    ]);
    active.stream.addEventListener("inactive", cleanup, { once: true });
    tracks.forEach(track => track.addEventListener("ended", cleanup, { once: true }));
    return processed;
  }

  function cleanup() {
    if (!active) return;
    clearInterval(hudTimer);
    hudTimer = 0;
    const graph = active;
    graph.clearReverbTimer();
    if (graph.meterNodes) {
      graph.meterNodes.inputTap.disconnect(graph.meterNodes.input);
      graph.meterNodes.outputTap.disconnect(graph.meterNodes.output);
      graph.meterNodes = null;
    }
    active = null;
    graph.context.close().catch(() => {});
  }

  function updateHud() {
    if (!state.hudVisible || document.visibilityState !== "visible" || !active) {
      clearInterval(hudTimer);
      hudTimer = 0;
      if (active?.meterNodes) {
        active.meterNodes.inputTap.disconnect(active.meterNodes.input);
        active.meterNodes.outputTap.disconnect(active.meterNodes.output);
        active.meterNodes = null;
      }
      return;
    }
    if (hudTimer) return;
    const inputAnalyser = active.context.createAnalyser();
    const outputAnalyser = active.context.createAnalyser();
    inputAnalyser.fftSize = outputAnalyser.fftSize = 128;
    const inputTap = active.source;
    const outputTap = active.context.createMediaStreamSource(active.destination.stream);
    inputTap.connect(inputAnalyser);
    outputTap.connect(outputAnalyser);
    active.meterNodes = { input: inputAnalyser, output: outputAnalyser, inputTap, outputTap };
    const inputSamples = new Float32Array(inputAnalyser.fftSize);
    const outputSamples = new Float32Array(outputAnalyser.fftSize);
    const spectrum = new Uint8Array(outputAnalyser.frequencyBinCount);
    hudTimer = setInterval(() => {
      if (document.visibilityState !== "visible" || !active) {
        updateHud(); return;
      }
      inputAnalyser.getFloatTimeDomainData(inputSamples);
      outputAnalyser.getFloatTimeDomainData(outputSamples);
      outputAnalyser.getByteFrequencyData(spectrum);
      let inputPeak = 0, outputPeak = 0, outputSquare = 0;
      for (let index = 0; index < inputSamples.length; index++) {
        inputPeak = Math.max(inputPeak, Math.abs(inputSamples[index]));
        outputPeak = Math.max(outputPeak, Math.abs(outputSamples[index]));
        outputSquare += outputSamples[index] * outputSamples[index];
      }
      const outputRms = Math.sqrt(outputSquare / outputSamples.length);
      const db = level => level > 0 ? 20 * Math.log10(level) : -100;
      lastStats = {
        inputDbfs: db(inputPeak), outputDbfs: db(outputPeak), rmsDbfs: db(outputRms),
        peakDbfs: db(outputPeak), clip: outputPeak >= 0.98,
        gainReductionDb: Math.max(
          0,
          -(Number(active.compressor.reduction) || 0) -
            (Number(active.limiter.reduction) || 0),
        ),
        sampleRate: active.context.sampleRate,
        latencyMs: (active.context.baseLatency + (active.context.outputLatency || 0)) * 1000,
        blocks: BLOCKS.filter(blockOn), spectrum: Array.from(spectrum),
        origin, mode: state.mode
      };
      window.__voiceFxBridge?.postMessage(JSON.stringify(lastStats));
    }, 67);
  }

  function set(values = {}) {
    if (values.parameters && typeof values.parameters === "object") {
      for (const [key, position] of Object.entries(values.parameters)) {
        if (Object.prototype.hasOwnProperty.call(MAPPING, key)) {
          state.parameters[key] = clamp(Math.round(position), 0, 100);
        }
      }
    }
    if (typeof values.enabled === "boolean") state.enabled = values.enabled;
    if (typeof values.hudVisible === "boolean") state.hudVisible = values.hudVisible;
    if (Array.isArray(values.blocks)) {
      state.blocks = new Set(values.blocks.filter(name => BLOCKS.includes(name)));
    }
    if (active) {
      if (!state.enabled) {
        active.source.disconnect();
        active.source.connect(active.destination);
      }
      else {
        active.updateParameters();
        active.connectActive();
      }
    }
    updateHud();
  }

  window.__voicefx = Object.freeze({
    set,
    setBlock(name, on) {
      if (!BLOCKS.includes(name)) return false;
      if (on) state.blocks.add(name); else state.blocks.delete(name);
      if (active && state.enabled) { active.updateParameters(); active.connectActive(); }
      return true;
    },
    setMode(mode) {
      if (mode !== "normal") return false;
      state.mode = "normal";
      return true;
    },
    panic() {
      state.enabled = false;
      window.__voiceFxBridge?.postMessage(JSON.stringify({ type: "panic" }));
      if (active) {
        active.source.disconnect();
        active.source.connect(active.destination);
      }
      updateHud();
    },
    getStats() { return { ...lastStats }; }
  });
  mediaDevices.getUserMedia = async function (constraints) {
    let stream;
    try {
      stream = await originalGetUserMedia(constraints);
    } catch (error) {
      reportMicrophoneError(error);
      throw error;
    }
    if (!constraints || !constraints.audio || !state.enabled) return stream;
    try {
      return await activate(stream);
    } catch (error) {
      reportMicrophoneError(error);
      if (active?.stream === stream) cleanup();
      return stream;
    }
  };
  if (originalEnumerateDevices) {
    mediaDevices.enumerateDevices = async function (...args) {
      try {
        return await originalEnumerateDevices(...args);
      } catch (error) {
        reportMicrophoneError(error);
        throw error;
      }
    };
  }
  const resumeAudioContext = () => {
    if (active?.context?.state === "suspended") {
      active.context.resume().catch(error =>
        reportMicrophoneError(error, "AudioContextSuspended"));
    }
  };
  document.addEventListener("pointerdown", resumeAudioContext, true);
  document.addEventListener("keydown", resumeAudioContext, true);
  document.addEventListener("visibilitychange", updateHud);
  updateHud();
})();
