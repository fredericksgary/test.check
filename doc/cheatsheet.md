# test.check cheatsheet

- Simple Generators
  - `(gen/return x)` - A constant generator that always generates `x`
  - `gen/boolean` - generates booleans (`true` and `false`)
  - `gen/nat` - generates small non-negative integers (useful for generating sizes of things)
  - `gen/large-integer` - generates a large range of integers
    - variant with options: `(gen/large-integer* {:min x :max y})`
