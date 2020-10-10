## 0.4.0

* added `to-chan`
* added `select-step`, like `reductions` but with multiple source signals that each get their own reducing function 
* flipped argument order of the `setup!` function passed to `splice` 
* added `indexed-updates`, a signal transformer useful for performing different effects based on which source signal has updated
* added `reductions` function, which works like `reducep` but has a better name and doesn't do an implicit `drop-repeats`
* removed `reducep` and `transducep`
* got rid of `j.z.i.signal/output-mult`
* calling core.async's `tap` function on a live graph created with `spawn` now supplies you with straight fresh values from the output signal, instead of batches of fresh & cached messages
  - this means that values of an output signal can never be `nil`, since core.async channels don't do `nil`s
* added `write-port`, a signal constructor that lets you treat the returned signal like a write-only `core.async` channel.

## 0.3.1

* fixed a bug that would throw an error if you tried to use a folding signal like `foldp` on a signal that emitted multiple values from a single event, e.