# Generating a Local Development Keystore

The backend uses a PKCS12 keystore for HTTPS. This file is **not** tracked in the
monorepo for security reasons.

## Generate a self-signed keystore

```bash
keytool -genkeypair -alias safevault \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 \
  -keystore src/main/resources/keystore.p12 \
  -dname "CN=server.safevaultapp.top,O=TTT,OU=IT,L=City,ST=State,C=CN"
```

Set the keystore password via the `KEYSTORE_PASSWORD` environment variable
or in `application-dev.yml`.

The backend `.gitignore` excludes `*.p12` files to prevent accidental commits.
