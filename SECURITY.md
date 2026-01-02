# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.2.x   | :white_check_mark: |
| < 1.2   | :x:                |

Only the latest release receives security updates. We recommend always using the most recent version.

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

### How to Report

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please use one of these methods:

1. **GitHub Private Vulnerability Reporting** (preferred): Use [GitHub's private vulnerability reporting](https://github.com/neptunestation-org/PJDBC/security/advisories/new) to submit a confidential report.

2. **Email**: Send details to davidaventimiglia@neptunestation.com with the subject line "PJDBC Security Vulnerability".

### What to Include

Please provide:

- A description of the vulnerability
- Steps to reproduce the issue
- Affected versions
- Any potential impact assessment
- Suggested fix (if available)

### Response Timeline

- **Initial Response**: Within 48 hours of receipt
- **Status Update**: Within 7 days with an assessment
- **Fix Timeline**: Depends on severity
  - Critical: Target fix within 7 days
  - High: Target fix within 30 days
  - Medium/Low: Target fix in next scheduled release

### Disclosure Policy

- We follow coordinated disclosure practices
- We will work with you to understand and resolve the issue
- We will credit reporters in the security advisory (unless anonymity is requested)
- We ask that you give us reasonable time to address the issue before public disclosure

## Security Best Practices

When using PJDBC in your applications:

1. **Keep Updated**: Always use the latest version to receive security patches.

2. **Credential Management**:
   - Never hardcode database credentials in JDBC URLs
   - Use environment variables or secure credential stores
   - When using `UserMapDriver`, protect the mapping properties file appropriately

3. **SQL Injection Prevention**:
   - PJDBC proxy drivers pass through SQL as-is; use parameterized queries
   - Be cautious with `FilterDriver` SQL transformations to avoid introducing vulnerabilities

4. **Data Masking**:
   - `DataMaskingDriver` operates on result sets only; it does not protect data in transit or at rest
   - Do not rely solely on masking for regulatory compliance

5. **Caching Drivers**:
   - Cached data may contain sensitive information
   - Configure appropriate TTLs and access controls for Redis/Memcached/Hazelcast
   - Consider encryption for cached data in distributed caches

6. **Logging**:
   - `LogDriver` may log sensitive data in SQL statements
   - Configure logging levels appropriately in production

## Security Updates

Security advisories will be published at:
https://github.com/neptunestation-org/PJDBC/security/advisories
