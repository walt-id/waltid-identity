/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const test = require('ava');

function isObject (item) {
  return item && typeof item === 'object' && !Array.isArray(item);
}

function mapDeepEqual (actual, expected, depth) {
  const sortedActualKeys = [...actual.keys()].sort();
  const sortedExpectedKeys = [...expected.keys()].sort();
  if (sortedActualKeys.length !== sortedExpectedKeys.length) {
    return false;
  }
  for (let i = 0; i < sortedActualKeys.length; i++) {
    const actualKey = sortedActualKeys[i];
    const expectedKey = sortedExpectedKeys[i];
    if (actualKey !== expectedKey) {
      return false;
    }
    if (!deepEqual(actual.get(actualKey), expected.get(expectedKey), depth + 1)) {
      return false;
    }
  }
  return true;
}

function objectDeepEqual (actual, expected, depth) {
  const sortedActualKeys = Object.keys(actual).sort();
  const sortedExpectedKeys = Object.keys(expected).sort();
  if (sortedActualKeys.length !== sortedExpectedKeys.length) {
    return false;
  }
  for (let i = 0; i < sortedActualKeys.length; i++) {
    const actualKey = sortedActualKeys[i];
    const expectedKey = sortedExpectedKeys[i];
    if (actualKey !== expectedKey) {
      return false;
    }
    if (!deepEqual(actual[actualKey], expected[expectedKey], depth + 1)) {
      return false;
    }
  }
  return true;
}

function arrayDeepEqual (actual, expected, depth) {
  if (actual.length !== expected.length) {
    return false;
  }
  for (let i = 0; i < actual.length; i++) {
    if (!deepEqual(actual[i], expected[i], depth + 1)) {
      return false;
    }
  }
  return true;
}

function deepEqual (actual, expected, depth) {
  const currentDepth = (depth !== undefined ? depth : 0);
  if (currentDepth === 50) {
    throw new Error('Structure is to deeply nested.');
  }

  if (actual instanceof Map && expected instanceof Map) {
    return mapDeepEqual(actual, expected, currentDepth);
  } else if (actual instanceof Set && expected instanceof Set) {
    throw new Error('Set is not supported.');
  } else if (isObject(actual) && isObject(expected)) {
    return objectDeepEqual(actual, expected, currentDepth);
  } else if (Array.isArray(actual) && Array.isArray(expected)) {
    return arrayDeepEqual(actual, expected, currentDepth);
  } else {
    return actual === expected;
  }
}

exports.deepEqual = deepEqual;

test('deep equal array', (t) => {
  const actual = [1, 2, 3, '4', [1, 2, 3], { hello: 'world', world: 'hello' }];
  const expected = [1, 2, 3, '4', [1, 2, 3], { hello: 'world', world: 'hello' }];
  t.true(deepEqual(actual, expected));
  expected.push(4);
  t.false(deepEqual(actual, expected));
});

test('deep equal deep array', (t) => {
  const actual = [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, 1]]]]]]]]]]]]];
  const expected = [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, [1, 1]]]]]]]]]]]]];
  t.true(deepEqual(actual, expected));
});

test('deep equal objects', (t) => {
  const actual = {
    world: 'hello',
    hello: 'world',
    complex: {
      world: 'hello',
      hello: 'world'
    }
  };
  const expected = {
    hello: 'world',
    world: 'hello',
    complex: {
      hello: 'world',
      world: 'hello'
    }
  };
  t.true(deepEqual(actual, expected));
  expected.test = 'test';
  t.false(deepEqual(actual, expected));
});

test('deep equal Map', (t) => {
  const actual = new Map();
  actual.set(1, 1);
  actual.set('hello', 'world');
  actual.set('object', { hello: 'world', world: 'hello' });
  const expected = new Map();
  expected.set(1, 1);
  expected.set('hello', 'world');
  expected.set('object', { hello: 'world', world: 'hello' });
  t.true(deepEqual(actual, expected));
  expected.set(2, 2);
  t.false(deepEqual(actual, expected));
});
