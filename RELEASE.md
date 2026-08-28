RELEASE_TYPE: patch

This patch adds a second published artifact, `dev.hegel:hegel-jna`, which binds the native engine
over [JNA](https://github.com/java-native-access/jna) and runs on Java 17+. The existing
`dev.hegel:hegel` artifact is unchanged: it binds over the Foreign Function and Memory API and
requires Java 22+.

Both artifacts expose the identical `dev.hegel` API and behave the same, so tests written against
one run unchanged against the other. Depend on exactly one of them — `hegel` on Java 22+, or
`hegel-jna` on older JVMs:

```xml
<dependency>
  <groupId>dev.hegel</groupId>
  <artifactId>hegel-jna</artifactId>
  <version>0.5.1</version>
</dependency>
```

`hegel-jna` pulls in `net.java.dev.jna:jna` as its only dependency. On JDK 24+ pass
`--enable-native-access=ALL-UNNAMED` to silence the JVM's native-access warning (the flag is
accepted on every supported JDK).
