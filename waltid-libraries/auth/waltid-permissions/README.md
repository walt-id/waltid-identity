<div align="center">
    <h1>Kotlin Multiplatform Permissions library</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>Define, apply, and check permissions seamlessly across different platforms.</p>
    <a href="https://walt.id/community">
        <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
    </a>
    <a href="https://www.linkedin.com/company/walt-id/">
        <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
    </a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## What This Library Contains

This library provides a fine-grained permission system for managing access control in your applications. It enables you to:

- **Define hierarchical permissions** using dot-notation resource targets (e.g., `orgA.tenant1.resource`)
- **Grant and deny permissions** for specific operations on resources
- **Efficiently check permissions** using a trie-based data structure for fast lookups
- **Support wildcard matching** for flexible permission patterns
- **Work across platforms** with native support for JVM (Kotlin/Java) and JavaScript

## Main Purpose

This library is designed to solve the challenge of fine-grained authorization in multi-tenant or hierarchical systems. It's particularly useful when you need to:

- Manage permissions for resources organized in a hierarchy (organizations, tenants, resources, etc.)
- Implement role-based access control (RBAC) with complex permission inheritance
- Support both allow and deny rules with explicit deny taking precedence
- Efficiently check permissions at runtime without linear scanning

## Key Concepts

### Permission Targets

Permissions are applied to **targets**, which are hierarchical resource identifiers using dot-notation. For example:
- `orgA` - targets an organization
- `orgA.tenant1` - targets a specific tenant within an organization
- `orgA.tenant1.resource` - targets a specific resource within a tenant

The library supports wildcard matching, so you can use `*` to match any segment:
- `orgA.*` - matches any tenant under `orgA`
- `orgA.*.issuer` - matches any issuer resource under any tenant in `orgA`

### Permission Operations

Each permission specifies an **operation** (action) that can be performed on a target:
- Specific operations like `issue`, `config`, `verify`
- The special operation `all` which grants or denies all operations on a target

### Permission Sets

A **PermissionSet** groups together multiple permissions that can be applied together. This is useful for representing roles or user permissions. Each set has:
- An identifier (ID) to distinguish different sets
- A collection of permissions with grant (`+`) or deny (`-`) operations

### Permission Checker

The **PermissionChecker** is the main class you use to:
1. Apply permission sets to build up an internal permission state
2. Check if a specific operation is allowed on a target

The checker uses two internal tries (allow and deny) to efficiently store and lookup permissions.

### Allow vs Deny

Permissions can be explicitly **granted** (using `+`) or **denied** (using `-`). The library follows these rules:
- A permission check passes only if the operation is explicitly allowed AND not denied
- Deny rules take precedence - if a permission is denied, it cannot be granted by another permission set
- More specific permissions override more general ones in the hierarchy

### Trie-Based Storage

The library uses a **compacted trie** (Patricia trie) data structure for efficient permission lookups. This allows:
- Fast permission checks even with large numbers of permissions
- Efficient hierarchical matching
- Memory-efficient storage of permission patterns

## Assumptions and Dependencies

This library makes several important assumptions:

- **Multiplatform Support**: Works on JVM (Kotlin/Java) and JavaScript. The same permission logic works identically across platforms.
- **Kotlin Coroutines**: Uses Kotlin's coroutines and Flow API for asynchronous operations. In JavaScript, these are automatically converted to Promises.
- **Dot-Notation Hierarchy**: Resource targets must use dot-separated identifiers to represent hierarchical relationships.
- **String-Based Operations**: Permission operations are represented as strings (e.g., `"issue"`, `"config"`).
- **Explicit Permissions**: Permissions must be explicitly granted; absence of a permission means denial.

## How to Use This Library

### Basic Workflow

1. **Create a PermissionChecker**: Instantiate a new `PermissionChecker` to manage permissions for a user or session.
2. **Define Permission Sets**: Create permission sets representing roles or permission groups using permission strings.
3. **Apply Permissions**: Apply one or more permission sets to the checker to build up the permission state.
4. **Check Permissions**: Use `checkPermission()` to verify if a specific operation is allowed on a target.

### Permission String Format

Permissions are defined using a simple string format:
```
target:operation1,operation2,...
```

Operations are prefixed with `+` for grant or `-` for deny:
- `orgA.tenant1:+issue,+config` - grants `issue` and `config` on `orgA.tenant1`
- `orgA.tenant1.sub1:-all` - denies all operations on `orgA.tenant1.sub1`

## JVM/Kotlin Usage

### Installation

Add the library as a dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}

dependencies {
    implementation("id.walt.permissions:waltid-permissions:<version>")
}
```

### Basic Usage

```kotlin
import id.walt.permissions.PermissionChecker
import id.walt.permissions.FlowPermissionSet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create a new PermissionChecker
    val checker = PermissionChecker()
    
    // Create a permission set using the infix function
    val permissionSet = "orgA.a" permissions flowOf(
        "orgA.tenant1:+issue,+config"
    )
    
    // Apply permissions
    checker.applyPermissions(permissionSet)
    
    // Check permissions
    val canIssue = checker.checkPermission("orgA.tenant1.abc", "issue")
    println("Can issue: $canIssue") // Outputs: true
    
    val canVerify = checker.checkPermission("orgA.tenant1.abc", "verify")
    println("Can verify: $canVerify") // Outputs: false
}
```

### Using Permission Sets Directly

You can also create permission sets using the companion object:

```kotlin
import id.walt.permissions.PermissionChecker
import id.walt.permissions.FlowPermissionSet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val checker = PermissionChecker()
    
    // Create permission set from flow of permission strings
    val role1 = FlowPermissionSet.fromPermissionStringsFlow(
        "admin",
        flowOf(
            "orgA:+all",
            "orgB.tenant1:+issue,+config"
        )
    )
    
    // Apply the permission set
    checker.applyPermissions(role1)
    
    // Check various permissions
    println(checker.checkPermission("orgA.tenant1.resource", "issue")) // true (all operations allowed)
    println(checker.checkPermission("orgB.tenant1.resource", "issue")) // true
    println(checker.checkPermission("orgB.tenant1.resource", "verify")) // false
}
```

### Working with Deny Permissions

Deny permissions take precedence over allow permissions:

```kotlin
import id.walt.permissions.PermissionChecker
import id.walt.permissions.FlowPermissionSet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val checker = PermissionChecker()
    
    // First grant permissions
    val grantSet = "grant" permissions flowOf(
        "orgA.tenant1:+all"
    )
    checker.applyPermissions(grantSet)
    
    // Then deny specific permissions
    val denySet = "deny" permissions flowOf(
        "orgA.tenant1.sub1:-all"
    )
    checker.applyPermissions(denySet)
    
    // Check permissions
    println(checker.checkPermission("orgA.tenant1.resource", "issue")) // true
    println(checker.checkPermission("orgA.tenant1.sub1.resource", "issue")) // false (explicitly denied)
}
```

### Using Permission Insights

For debugging and auditing, you can get detailed insights about why a permission check passed or failed:

```kotlin
import id.walt.permissions.PermissionChecker
import id.walt.permissions.FlowPermissionSet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val checker = PermissionChecker()
    
    checker.applyPermissions("role1" permissions flowOf(
        "orgA.tenant1:+issue,+config"
    ))
    
    // Get detailed permission insights
    val insights = checker.checkPermissionInsights("orgA.tenant1.resource", "issue")
    insights.print() // Prints: issue on orgA.tenant1: ✅ | allows: [[issue on orgA.tenant1]] / denies: None
}
```

## JavaScript Usage

### Installation

Install the library via npm:

```bash
npm install waltid-permissions
```

### Importing the Library

```javascript
import lib from 'waltid-permissions';
```

### Example Code

```javascript
// Create a new instance of PermissionChecker
const permissionChecker = new lib.id.walt.permissions.PermissionChecker();

// Create a permission set from a permission string
const permissionSet = lib.id.walt.permissions.FlowPermissionSet.Companion.fromPermissionStringFlow(
    'orgA.a',  // Permission set ID
    'orgA.tenant1:+issue,+config'  // Permission string
);

// Apply permissions asynchronously
await permissionChecker.applyPermissionsAsync(permissionSet);

// Check if a specific permission is granted
const canIssue = permissionChecker.checkPermission("orgA.tenant1.abc", "issue");
console.log(canIssue); // Outputs: true

// Check another operation
const canConfig = permissionChecker.checkPermission("orgA.tenant1.abc", "config");
console.log(canConfig); // Outputs: true

// Check a denied operation
const canVerify = permissionChecker.checkPermission("orgA.tenant1.abc", "verify");
console.log(canVerify); // Outputs: false
```

### Multiple Permission Sets

You can apply multiple permission sets to build up complex permission models:

```javascript
const checker = new lib.id.walt.permissions.PermissionChecker();

// Apply role-based permissions
const adminRole = lib.id.walt.permissions.FlowPermissionSet.Companion.fromPermissionStringFlow(
    'admin',
    'orgA:+all'
);
await checker.applyPermissionsAsync(adminRole);

const tenantRole = lib.id.walt.permissions.FlowPermissionSet.Companion.fromPermissionStringFlow(
    'tenant-user',
    'orgA.tenant1:+issue,+verify'
);
await checker.applyPermissionsAsync(tenantRole);

// Check permissions
console.log(checker.checkPermission("orgA.tenant1.resource", "issue")); // true
console.log(checker.checkPermission("orgA.tenant1.resource", "config")); // true (from admin role)
```

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`Permissions.kt`**: Contains the main `PermissionChecker` class with methods for applying and checking permissions
- **`PermissionSet.kt`**: Defines `PermissionSet` and `FlowPermissionSet` for grouping permissions
- **`Permission.kt`**: Defines the `Permission` data class and parsing logic
- **`PermissionTrie.kt`**: Implements the trie data structure for efficient permission lookups
- **`PermissionedResourceTarget.kt`**: Handles hierarchical resource target matching
- **Test files**: Located in `src/commonTest`, these provide comprehensive examples of permission patterns and edge cases



## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
