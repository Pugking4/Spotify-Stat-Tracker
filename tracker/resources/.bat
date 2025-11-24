keytool -genkeypair -alias myserver -keyalg RSA -keysize 2048 -validity 365 \
 -keystore keystore.jks -storepass password -keypass password \
 -dname "CN=localhost, OU=Development, O=MyCompany, L=City, ST=State, C=US"
