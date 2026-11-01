const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");
const ranges = JSON.parse(
  fs.readFileSync(path.join(root, "app/src/main/assets/voicefx-mapping.json"), "utf8"),
);
const source = fs.readFileSync(
  path.join(root, "app/src/main/assets/voicefx-scale.js"),
  "utf8",
);
const map = vm.runInNewContext(`(${source})`);

for (const [name, range] of Object.entries(ranges)) {
  const normal = Array.from({ length: 101 }, (_, index) => map(index, false, range));
  const extended = Array.from({ length: 1001 }, (_, index) => map(index, true, range));
  const increasing = range[1] >= range[0];
  assert.equal(extended[0], normal[0], `${name}: lower bound differs`);
  assert.equal(extended[100], normal[100], `${name}: scale is discontinuous at 100`);
  assert.equal(extended[1000], range[2], `${name}: extended maximum differs`);
  for (const values of [normal, extended]) {
    for (let index = 1; index < values.length; index++) {
      assert.ok(
        increasing ? values[index] >= values[index - 1] : values[index] <= values[index - 1],
        `${name}: scale is not monotonic at ${index}`,
      );
    }
  }
}

assert.equal(map(-1, false, ranges.outputGain), 0);
assert.equal(map(101, false, ranges.outputGain), 1);
assert.equal(map(1001, true, ranges.outputGain), 1);
console.log("voicefx-scale: all mappings are continuous, monotonic, and bounded");
