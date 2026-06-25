RELEASE_TYPE: minor

1) the UUID generator return type is changed from String to java.util.UUID
2) version configuration is exposed in the Java API, with version validation currently done on the client side
3) uuidStrings() is retained as a separate generator
