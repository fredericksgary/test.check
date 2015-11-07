# test.check cheatsheet

## Simple Generators

- `(gen/return x)` — A constant generator that always generates `x`
- `gen/boolean` — generates booleans (`true` and `false`)

### Numbers

- `gen/nat` — generates small non-negative integers (useful for generating sizes of things)
- `gen/large-integer` — generates a large range of integers
  - variant with options: `(gen/large-integer* {:min x, :max y})`
- `gen/double` — generates a large range of doubles (w/ infinities & `NaN`)
  - variant with options: `(gen/double* {:min x, :max y, :infinite? true, :NaN? true})`
- `gen/ratio` — generates ratios (sometimes integers)

### Characters & Strings & Things

- `gen/char` — generates characters
- `gen/char-ascii` — generates printable ASCII characters
- `gen/char-alphanumeric` — generates alphanumeric ASCII characters
- `gen/char-alpha` — generates alphabetic ASCII characters
- `gen/string` — generates a string
- `gen/string-ascii` — generates a string using `gen/char-ascii`
- `gen/string-alphanumeric` — generates a string using `gen/char-alphanumeric`
- `gen/keyword` — generates keywords
- `gen/keyword-ns` — generates namespaced keywords
- `gen/symbol` — generates symbols
- `gen/symbol-ns` — generates namespaced symbols

## Heterogeneous Collections

- `(gen/tuple g1 g2 ...)` — generates vectors `[x1 x2 ...]` where `x1`
  is drawn from `g1`, `x2` from `g2`, etc.
- `(gen/hash-map k1 g1, k2 g2, ...)` — generates maps `{k1 v1, k2 v2, ...}`
  where `v1` is drawn from `g1`, `v2` from `g2`, etc.


## Homogeneous Collections

- `(gen/vector g)` — generates vectors of elements from `g`
  - Variants:
    - `(gen/vector g num-elements)`
    - `(gen/vector g min-elements max-elements)`
- `(gen/list g)` — generates lists of elements from `g`
- `(gen/set g)` — generates sets of elements from `g`
  - Variants:
    - `(gen/set g {:num-elements x, :max-tries 20})`
    - `(gen/set g {:min-elements x, :max-elements y, :max-tries 20})`
- `(gen/sorted-set g)` — just like `gen/set`, but generates sorted-sets
- `(gen/vector-distinct g)` — same signature as `gen/set`, but generates
  vectors of distinct elements
- `(gen/list-distinct g)` — same signature as `gen/set`, but generates
  lists of distinct elements
- `(gen/vector-distinct-by key-fn g)` — generates vectors of elements
  where `(apply distinct? (map key-fn the-vector))`
  - same opts as `gen/set`
- `(gen/list-distinct-by key-fn g)` — generates list of elements
  where `(apply distinct? (map key-fn the-list))`
  - same opts as `gen/set`

## Combinators

- `(gen/let [x g] y)` — **macro**, like `clojure.core/let`, where
  the right-hand bindings are generators and the left-hand are
  generated values; creates a generator
  - same functionality as `gen/fmap` and `gen/bind`
- `(gen/fmap f g)` — creates a generator that generates `(f x)` for
  `x` generated from `g`
- `(gen/bind g f)` — similar to `gen/fmap`, but where `(f x)` is itself
  a generator and `(gen/bind g f)` generates values from `(f x)`
- `(gen/such-that pred g)` — returns a new generator that generates
  only elements from `g` that match `pred`
  - Variants: `(gen/such-that pred g max-tries)`

## Sizing & shrinking control

- `(gen/resize n g)` — creates a variant of `g` whose `size` parameter
  is always `n`
- `(gen/scale f g)` — creates a variant of `g` whose `size` parameter
  is `(f size)`
