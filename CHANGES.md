## 0.4.0

* added `to-chan`
* added `select-step`, like `reductions` but with multiple source signals that each get their own reducing function 
* flipped argument order of the `setup!` function passed to `splice` 
* added `indexed-updates`, a signal transformer useful for performing different effects based on which source signal has updated
* added `reductions` function, which works like `reducep` but