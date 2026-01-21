## 2024-05-20 - UserMapDriver Plaintext Credential Storage

**Vulnerability:** The `UserMapDriver` is designed to load database credentials from a properties file (`org.pjdbc.UserMapDriver.UserMapFile`) where passwords are stored in plaintext. The format `app_user=db_user/db_password` makes it highly likely that production credentials will be exposed.

**Learning:** The driver's design prioritizes a specific type of credential mapping over security. While `SECURITY.md` mentions protecting the file, this is insufficient. The application code itself encourages insecure practices. A driver that handles credentials should never be designed to expect them in plaintext.

**Prevention:** Future credential-handling mechanisms must use a secure storage method, such as environment variables, a KMS, or other secrets management tools. Avoid file-based credential loading unless the contents are encrypted. For this specific driver, adding prominent warnings is a necessary mitigation to alert developers to the risk.