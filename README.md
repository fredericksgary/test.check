# My [test.check](https://github.com/clojure/test.check) fork

This is a (hopefully) temporary fork of test.check that is primarily
motivated by my experience with using test.check for slower,
higher-level tests.

## Obtainage

`[com.gfredericks.forks.org.clojure/test.check "0.6.1-p1"]`

## User-visible differences

- When a failure is found, this fact is printed immediately,
  along with the "key" (see below) for the failure
- During shrinking there is at least a character (`.`) printed
  for each time the test is run, and every time a smaller failing
  input is found the full "key" is printed as well
- There is a `clojure.test.check/retry` function that takes a
  property and a "key"
- There is a `clojure.test.check/resume` function that takes a
  property and a "key"
- [TCHECK-42](http://dev.clojure.org/jira/browse/TCHECK-42) has been
  patched

## What is a key?

A key is a triple `[rng size shrink-path]` that can be used to
reproduce any individual test run caused by normal use.

## User-invisible differences

- Properties now generate delays of the test result instead of
  the test result directly, which allows walking the shrink tree
  faster
- `defspec` now exposes the underlying property as metadata so
  that the `retry` and `resume` functions can be passed the test
  var's value (a function) directly
