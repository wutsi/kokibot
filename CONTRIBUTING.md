# Contributing to Kokibot

Thank you for your interest in contributing to Kokibot! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Code Standards](#code-standards)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Issue Guidelines](#issue-guidelines)

---

## Code of Conduct

We are committed to providing a welcoming and inclusive environment for all contributors. Please:

- Be respectful and considerate in all interactions
- Provide constructive feedback
- Focus on what is best for the community
- Show empathy towards other community members

---

## Getting Started

### Prerequisites

Before you begin, ensure you have the following installed:

- **Java 17** or higher
- **Maven 3.6+**
- **Git**
- **GraalVM** (optional, for Python tool development)
- **IDE** - IntelliJ IDEA, VS Code, or your preferred Kotlin IDE

### Fork and Clone

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/kokibot.git
   cd kokibot
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/wutsi/kokibot.git
   ```

---

## Development Setup

### Build the Project

```bash
# Full build with tests
mvn clean install

# Run tests only
mvn test

# Run the application
mvn spring-boot:run
```

### Setup Configuration

Create your test configuration:

```bash
mkdir -p ~/kokibot/config
cat > ~/kokibot/config/settings.json << 'EOF'
{
  "assistant": {
    "max-iterations": 10
  },
  "llm": {
    "type": "deepseek",
    "api-key": "${DEEPSEEK_API_KEY}",
    "model": "deepseek-chat"
  },
  "memory": {
    "window": 3,
    "compaction-frequency": 6
  }
}
EOF
```

### Environment Variables

Set up your environment variables:

```bash
export DEEPSEEK_API_KEY="your-api-key"
# or
export KIMI_API_KEY="your-api-key"

# Optional for email tools
export MAIL_USERNAME="your-email"
export MAIL_PASSWORD="your-password"

# Optional for Telegram channel
export TELEGRAM_TOKEN="your-telegram-token"
```

---

## How to Contribute

### Types of Contributions

We welcome contributions of all kinds:

- **Bug Fixes** - Fix issues and improve stability
- **New Features** - Add new tools, skills, channels, or LLM providers
- **Documentation** - Improve docs, add examples, fix typos
- **Tests** - Add or improve test coverage
- **Performance** - Optimize code and improve efficiency
- **Code Quality** - Refactor code, improve readability

### Contribution Workflow

1. **Create a branch** for your contribution:
   ```bash
   git checkout -b feature/amazing-feature
   # or
   git checkout -b fix/bug-description
   ```

2. **Make your changes** following our [Code Standards](#code-standards)

3. **Write or update tests** to cover your changes

4. **Run tests and linting**:
   ```bash
   mvn test
   mvn antrun:run@ktlint
   ```

5. **Commit your changes** with clear commit messages:
   ```bash
   git commit -m "Add feature: description of what you added"
   ```

6. **Push to your fork**:
   ```bash
   git push origin feature/amazing-feature
   ```

7. **Open a Pull Request** against the `master` branch

---

## Code Standards

### Kotlin Style Guide

We follow the Kotlin coding conventions with ktlint enforcement.

**Key Guidelines:**

- **Indentation:** 4 spaces (no tabs)
- **Line Length:** No strict limit, but aim for readability
- **Braces:** K&R style (opening brace on same line)
- **Naming:**
  - Classes: `PascalCase`
  - Functions/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Files: Match class name

**Example:**

```kotlin
class MyTool : Tool {
    private val maxRetries = 3
    
    override fun metadata(): ToolMetadata {
        return ToolMetadata(
            name = "my_tool",
            description = "Tool description",
            parameters = listOf(
                ToolParameter(
                    name = "param1",
                    description = "Parameter description",
                    type = ToolParameterType.STRING,
                    required = true
                )
            )
        )
    }
    
    override fun exec(arguments: Map<*, *>): String {
        val param1 = arguments["param1"]?.toString()
            ?: throw IllegalArgumentException("param1 is required")
        
        // Implementation
        return "Result"
    }
}
```

### Code Formatting

Run ktlint to format your code:

```bash
# Check formatting
mvn antrun:run@ktlint

# Auto-format
mvn antrun:run@ktlint-format
```

### Documentation

- Add KDoc comments for public APIs
- Include parameter descriptions
- Provide usage examples where helpful
- Update relevant documentation files

**Example:**

```kotlin
/**
 * Searches emails in the mailbox based on criteria.
 *
 * @param from Filter by sender email address
 * @param subject Filter by subject keywords
 * @param dateFrom Filter by start date (ISO 8601)
 * @param dateTo Filter by end date (ISO 8601)
 * @return List of matching email message IDs
 */
fun searchEmails(
    from: String?,
    subject: String?,
    dateFrom: String?,
    dateTo: String?
): List<String>
```

---

## Testing Requirements

### Coverage Requirements

- **Minimum Line Coverage:** 93%
- **Minimum Class Coverage:** 93%
- Coverage enforced by JaCoCo during build

### Test Guidelines

1. **Unit Tests** for all new code
2. **Integration Tests** for components that interact
3. **Mock external dependencies** using Mockito Kotlin
4. **Test edge cases and error conditions**
5. **Use descriptive test names**

### Test Structure

```kotlin
class MyToolTest {
    private lateinit var tool: MyTool
    private lateinit var context: Context
    
    @BeforeEach
    fun setUp() {
        context = mock()
        tool = MyTool()
        tool.init(emptyMap(), context)
    }
    
    @Test
    fun `exec should return result when valid input provided`() {
        // Given
        val arguments = mapOf("param1" to "value1")
        
        // When
        val result = tool.exec(arguments)
        
        // Then
        assertEquals("Expected result", result)
    }
    
    @Test
    fun `exec should throw exception when required parameter missing`() {
        // Given
        val arguments = emptyMap<String, Any>()
        
        // When/Then
        assertThrows<IllegalArgumentException> {
            tool.exec(arguments)
        }
    }
    
    @AfterEach
    fun tearDown() {
        tool.destroy()
    }
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MyToolTest

# Run specific test method
mvn test -Dtest=MyToolTest#exec_should_return_result

# View coverage report
mvn clean test
open target/site/jacoco/index.html
```

---

## Pull Request Process

### Before Submitting

✅ **Checklist:**

- [ ] Code follows style guidelines
- [ ] All tests pass
- [ ] Code coverage meets minimum (93%)
- [ ] ktlint passes
- [ ] Commit messages are clear and descriptive
- [ ] Documentation updated (if applicable)
- [ ] No merge conflicts with master

### PR Title Format

Use clear, descriptive titles:

- `Add: New web_scrape tool for dynamic content`
- `Fix: Email unsubscribe failing for some newsletter formats`
- `Improve: Optimize memory compaction performance`
- `Docs: Add examples for custom skill creation`

### PR Description Template

```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Changes Made
- Change 1
- Change 2
- Change 3

## Testing
Describe how you tested these changes.

## Screenshots (if applicable)
Add screenshots for UI changes.

## Related Issues
Closes #123
Relates to #456
```

### Review Process

1. **Automated Checks** - GitHub Actions run tests and linting
2. **Code Review** - Maintainers review your code
3. **Feedback** - Address review comments
4. **Approval** - PR approved by maintainer
5. **Merge** - PR merged to master

### After Merge

- Delete your feature branch
- Pull latest master
- Update your fork

---

## Issue Guidelines

### Reporting Bugs

When reporting bugs, please include:

**Bug Report Template:**

```markdown
## Description
Clear description of the bug.

## Steps to Reproduce
1. Step one
2. Step two
3. Step three

## Expected Behavior
What you expected to happen.

## Actual Behavior
What actually happened.

## Environment
- Kokibot version: 
- Java version: 
- OS: 
- LLM provider: 

## Logs/Error Messages
```
Paste relevant logs or error messages
```

## Additional Context
Any other relevant information.
```

### Requesting Features

When requesting features, please include:

**Feature Request Template:**

```markdown
## Feature Description
Clear description of the feature.

## Use Case
Why this feature would be useful.

## Proposed Implementation
(Optional) How you think it could be implemented.

## Alternatives Considered
Other approaches you've considered.

## Additional Context
Any other relevant information.
```

---

## Adding New Components

### Adding a New Tool

1. **Create tool class** in `src/main/kotlin/com/wutsi/kokibot/tools/`
2. **Implement Tool interface**
3. **Add to ContextFactory.discoverTools()**
4. **Write tests** in `src/test/kotlin/com/wutsi/kokibot/tools/`
5. **Update documentation** in `docs/references/tools.md`

See [AGENT.md](AGENT.md#tools-system) for detailed guide.

### Adding a New Skill

1. **Create skill directory** in `~/kokibot/skills/my-skill/`
2. **Create SKILL.md** with metadata and instructions
3. **Add test skill** in `src/test/resources/skills/`
4. **Write tests** for skill activation
5. **Update documentation** in `docs/references/skills.md`

See [AGENT.md](AGENT.md#skills-system) for detailed guide.

### Adding a New Channel

1. **Create channel class** in `src/main/kotlin/com/wutsi/kokibot/channel/`
2. **Extend Channel abstract class**
3. **Add to ChannelFactory.create()**
4. **Write tests** in `src/test/kotlin/com/wutsi/kokibot/channel/`
5. **Update documentation**

See [AGENT.md](AGENT.md#channels-system) for detailed guide.

### Adding a New LLM Provider

1. **Create provider class** in `src/main/kotlin/com/wutsi/kokibot/llm/`
2. **Implement LLM interface**
3. **Create client class** for API integration
4. **Add to LLMFactory.create()**
5. **Write tests** for both provider and client
6. **Update documentation** in `docs/references/llm.md`

---

## Questions?

If you have questions or need help:

- **GitHub Discussions** - Ask questions and discuss ideas
- **GitHub Issues** - Report bugs or request features
- **Documentation** - Check [AGENT.md](AGENT.md) and [docs/](docs/)

---

## License

By contributing to Kokibot, you agree that your contributions will be licensed under the MIT License.

---

**Thank you for contributing to Kokibot! 🚀**

We appreciate your time and effort in making this project better.
