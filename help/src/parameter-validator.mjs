export function validateParameters(testDefinition, submitted) {
  if (!submitted || typeof submitted !== 'object' || Array.isArray(submitted)) throw new Error('parameters must be an object');
  const known = new Set(testDefinition.parameters.map((parameter) => parameter.id));
  for (const key of Object.keys(submitted)) if (!known.has(key)) throw new Error(`unknown parameter '${key}'`);

  const bindings = {};
  for (const parameter of testDefinition.parameters) {
    let value = Object.hasOwn(submitted, parameter.id) ? submitted[parameter.id] : parameter.default;
    if (value === undefined) {
      if (parameter.required) throw new Error(`parameter '${parameter.id}' is required`);
      continue;
    }
    if (parameter.type === 'text' || parameter.type === 'password') {
      if (typeof value !== 'string') throw new Error(`parameter '${parameter.id}' must be a string`);
      if (value.length === 0 && !parameter.allowEmpty) throw new Error(`parameter '${parameter.id}' must not be empty`);
      if (parameter.validation?.notBlank && value.trim().length === 0) throw new Error(`parameter '${parameter.id}' must not be blank`);
      if (parameter.validation?.pattern && !new RegExp(parameter.validation.pattern).test(value)) throw new Error(`parameter '${parameter.id}' has an invalid format`);
    } else if (parameter.type === 'number') {
      if (!Number.isSafeInteger(value)) throw new Error(`parameter '${parameter.id}' must be a safe integer`);
      if (parameter.minimum !== undefined && value < parameter.minimum) throw new Error(`parameter '${parameter.id}' is below its minimum`);
      if (parameter.maximum !== undefined && value > parameter.maximum) throw new Error(`parameter '${parameter.id}' is above its maximum`);
      value = String(value);
    } else if (parameter.type === 'boolean') {
      if (typeof value !== 'boolean') throw new Error(`parameter '${parameter.id}' must be boolean`);
      value = String(value);
    }
    bindings[parameter.binding.name] = value;
  }
  return bindings;
}
