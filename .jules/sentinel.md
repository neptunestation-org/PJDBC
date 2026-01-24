## 2024-07-25 - Plaintext Password Vulnerability in UserMapDriver
**Vulnerability:** The `UserMapDriver` class loads database credentials from a properties file in plaintext, which is a critical security vulnerability.
**Learning:** When a direct fix for a security vulnerability is a breaking change, an acceptable alternative is to add a prominent, non-ignorable warning to alert developers to the risk. This provides a valuable security enhancement without disrupting existing functionality.
**Prevention:** When designing features that handle sensitive data, always prioritize secure-by-default options. If a less secure option is provided for convenience, it must be accompanied by clear, unavoidable warnings about the associated risks.
