# jwiki

An Android app which displays files created by
[wiki-builder](https://github.com/rsookram/wiki-builder). I made this because
search was [broken](https://github.com/kiwix/kiwix-android/issues/3587) in
Kiwix for Japanese (among other languages) and there doesn't seem to be
[a new enough](https://github.com/openzim/libzim/issues/794#issuecomment-2526066543)
`zim` file for Wikipedia yet where search would work.

## Building

Run the following command from the root of the repository to make a debug
build:

```shell
./gradlew assembleDebug
```

Making a release build is similar, but requires environment variables to be set
to indicate how to sign the APK:

```shell
STORE_FILE='...' STORE_PASSWORD='...' KEY_ALIAS='...' KEY_PASSWORD='...' ./gradlew assembleRelease
```
