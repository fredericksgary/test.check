# test.check cheatsheet

- Simple Generators
  - `(gen/return x)` - A constant generator that always generates `x`
  - `gen/boolean` - generates booleans (`true` and `false`)
  - `gen/nat` - generates small non-negative integers (useful for generating sizes of things)
  - `gen/large-integer` - generates a large range of integers
    - variant with options: `(gen/large-integer* {:min x, :max y})`
  - `gen/double` - generates a large range of doubles (w/ infinities & `NaN`)
    - variant with options: `(gen/double* {:min x, :max y, :infinite? true, :NaN? true})`
  - `gen/ratio` - generates ratios (sometimes integers)
