(position, extended, range) => {
  const maximum = extended ? 1000 : 100;
  const value = Math.min(maximum, Math.max(0, Math.round(Number(position) || 0)));
  const fraction = Math.min(value, 100) / 100;
  const normal = range[0] + (range[1] - range[0]) * fraction;
  if (!extended || value <= 100) return normal;
  const expanded = (value - 100) / 900;
  return range[1] + (range[2] - range[1]) * Math.pow(expanded, range[3]);
}
