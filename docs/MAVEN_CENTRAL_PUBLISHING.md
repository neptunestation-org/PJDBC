# Publishing PJDBC to Maven Central

This guide covers the steps to publish PJDBC to Maven Central via Sonatype OSSRH.

## Prerequisites

- GPG installed (`gpg --version`)
- Maven 3.6+ installed
- Java 17+ installed

## Step 1: Create a Sonatype OSSRH Account

1. Go to https://central.sonatype.com/
2. Click "Sign In" and create an account (you can use GitHub OAuth)
3. After signing in, you'll have access to the Sonatype Central Publisher portal

## Step 2: Register Your Namespace

You need to prove ownership of the `org.pjdbc` namespace (derived from a domain you control) or use a GitHub-based namespace.

### Option A: GitHub-based namespace (Recommended for open source)

1. In Sonatype Central, go to "Namespaces"
2. Click "Add Namespace"
3. Select "GitHub" as the provider
4. Authorize Sonatype to verify your GitHub account
5. Your namespace will be `io.github.{your-github-username}` or `io.github.{org-name}`

**Note:** If using GitHub namespace, update `pom.xml`:
```xml
<groupId>io.github.neptunestation-org</groupId>
```

### Option B: Domain-based namespace

1. In Sonatype Central, go to "Namespaces"
2. Click "Add Namespace"
3. Enter your domain (e.g., `org.pjdbc` requires ownership of `pjdbc.org`)
4. Verify ownership via DNS TXT record or other method provided

## Step 3: Generate GPG Keys

Maven Central requires all artifacts to be signed with GPG.

### Generate a new key pair

```bash
gpg --gen-key
```

Follow the prompts:
- Key type: RSA and RSA (default)
- Key size: 4096
- Expiration: 0 (never expires) or set as desired
- Real name: Your Name
- Email: your-email@example.com
- Passphrase: Choose a secure passphrase (you'll need this for signing)

### List your keys

```bash
gpg --list-keys
```

Output will show something like:
```
pub   rsa4096 2024-01-15 [SC]
      ABCD1234EFGH5678IJKL9012MNOP3456QRST7890
uid           [ultimate] Your Name <your-email@example.com>
sub   rsa4096 2024-01-15 [E]
```

The long hex string is your key ID.

### Publish your public key to a key server

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

## Step 4: Configure Maven Settings

Create or edit `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_TOKEN</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

### Get your Sonatype token

1. Log in to https://central.sonatype.com/
2. Click your username → "View Account"
3. Go to "Generate User Token"
4. Copy the username and password values to your `settings.xml`

## Step 5: Update pom.xml for Central Publishing

The pom.xml is already configured with most requirements. Verify these sections exist:

### Required metadata

```xml
<name>PJDBC</name>
<description>A proxying, filtering JDBC driver framework</description>
<url>https://github.com/neptunestation-org/PJDBC</url>

<licenses>
  <license>
    <name>MIT License</name>
    <url>https://opensource.org/licenses/MIT</url>
  </license>
</licenses>

<developers>
  <developer>
    <name>PJDBC Contributors</name>
    <organization>neptunestation-org</organization>
    <organizationUrl>https://github.com/neptunestation-org</organizationUrl>
  </developer>
</developers>

<scm>
  <connection>scm:git:git://github.com/neptunestation-org/PJDBC.git</connection>
  <developerConnection>scm:git:ssh://github.com:neptunestation-org/PJDBC.git</developerConnection>
  <url>https://github.com/neptunestation-org/PJDBC</url>
</scm>
```

### Required plugins

- `maven-source-plugin` - generates source JAR
- `maven-javadoc-plugin` - generates javadoc JAR
- `maven-gpg-plugin` - signs artifacts

These are already in the pom.xml.

## Step 6: Build and Sign Artifacts

```bash
# Clean build with all artifacts
mvn clean verify -DskipTests

# This produces:
# - PJDBC-1.2.jar (main artifact)
# - PJDBC-1.2-sources.jar
# - PJDBC-1.2-javadoc.jar
# - *.asc signature files for each
```

## Step 7: Deploy to Maven Central

### Option A: Using Central Publishing Portal (Recommended)

1. Build the artifacts:
   ```bash
   mvn clean package -DskipTests
   ```

2. Create a bundle:
   ```bash
   cd target
   jar -cvf bundle.jar PJDBC-1.2.pom PJDBC-1.2.jar PJDBC-1.2-sources.jar PJDBC-1.2-javadoc.jar PJDBC-1.2.pom.asc PJDBC-1.2.jar.asc PJDBC-1.2-sources.jar.asc PJDBC-1.2-javadoc.jar.asc
   ```

3. Upload to Sonatype Central:
   - Go to https://central.sonatype.com/
   - Click "Publish" → "Upload a Deployment Bundle"
   - Upload the bundle.jar
   - Verify the components
   - Click "Publish"

### Option B: Using Maven Deploy (Legacy OSSRH)

```bash
mvn clean deploy -DskipTests
```

This uses the nexus-staging-maven-plugin configured in pom.xml.

## Step 8: Verify Publication

After publishing:

1. Check https://central.sonatype.com/ for your artifact status
2. Wait 10-30 minutes for sync to Maven Central
3. Search at https://search.maven.org/ for `org.pjdbc`
4. Verify the artifact appears with all JARs (main, sources, javadoc)

## Troubleshooting

### GPG signing fails

```bash
# Check if GPG agent is running
gpg-agent --daemon

# Test signing manually
echo "test" | gpg --clearsign
```

### "401 Unauthorized" errors

- Verify your token in `~/.m2/settings.xml`
- Regenerate token at Sonatype Central if needed
- Ensure `<server><id>` matches the repository ID

### Missing Javadoc or Sources

```bash
# Generate manually
mvn source:jar javadoc:jar
```

### Namespace not verified

- Complete the namespace verification process in Sonatype Central
- For GitHub namespaces, ensure the OAuth connection is still valid

## Quick Reference Commands

```bash
# Full release build
mvn clean verify -DskipTests

# Deploy to staging
mvn clean deploy -DskipTests

# List GPG keys
gpg --list-keys

# Export public key (for sharing)
gpg --armor --export YOUR_KEY_ID > public-key.asc
```

## Links

- Sonatype Central: https://central.sonatype.com/
- Maven Central Search: https://search.maven.org/
- GPG Key Servers:
  - https://keyserver.ubuntu.com/
  - https://keys.openpgp.org/
- Sonatype Documentation: https://central.sonatype.org/publish/
