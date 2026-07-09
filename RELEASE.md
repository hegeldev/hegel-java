RELEASE_TYPE: major

`Generators.fromRegex` now generates strings where the *entire* string matches
the pattern, instead of strings that merely *contain* a match somewhere within
them.

```java
// before: could generate "xy123z" — the pattern only had to appear somewhere
// after: generates only exact matches like "123"
fromRegex("[0-9]{3}")
// restore the old behaviour:
fromRegex("[0-9]{3}").fullmatch(false)
```
