RELEASE_TYPE: major

`Generators.fromRegex` now generates strings where the *entire* string matches
the pattern, instead of strings that merely *contain* a match somewhere within
them. To restore the old behaviour, opt out with
`fromRegex(pattern).fullmatch(false)`.
