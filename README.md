## org.nypl.audiobook.demo.android

### Building

The application pulls an audiobook manifest from one of a set of
selectable URIs. The application includes a set of built-in URIs
pointing to various example books around the internet, and also
supports providing a default URI at compile-time to test a specific
book. The default URI must be specified at compile-time by defining
it in your `local.properties` file:

```bash
$ cat local.properties
feed.borrow_uri = http://qa.circulation.librarysimplified.org/NYNYPL/works/abcdef/fulfill/1
```

If you don't have an NYPL account, you can specify the URL of the 
manifest for any "open access" audio book.

Then, compile the code:

```bash
$ ./gradlew clean assembleDebug
```

### Findaway Support

If you wish to compile in support for the Findaway Audio Engine, enable the following
in your local `gradle.properties` file:

```
org.nypl.audiobook.demo.with_findaway = true
```
