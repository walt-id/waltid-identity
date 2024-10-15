<div align="center">
    <h1>Kotlin Multiplatform Permissions library</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>Define, apply, and check permissions seamlessly across different platforms.<p>
    <a href="https://walt.id/community">
        <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
    </a>
    <a href="https://twitter.com/intent/follow?screen_name=walt_id">
        <img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
    </a>
</div>

## Installation

You can install the library via npm:

```bash
npm install waltid-permissions
```

## Usage

Here's a quick guide on how to use the library in your JavaScript environment.

### Importing the Library

First, import the library into your JavaScript file:

```javascript
import lib from 'waltid-permissions';
```

### Example Code

Below is an example of how to create a `PermissionChecker`, define a permission set, apply permissions, and check specific permissions:

```javascript
// Create a new instance of PermissionChecker
const permissionChecker = new lib.id.walt.permissions.PermissionChecker();

// Create a permission set from a permission string
const permissionSet = lib.id.walt.permissions.FlowPermissionSet.Companion.fromPermissionStringFlow('orgA.a', 'orgA.tenant1:+issue,+config');

// Apply permissions asynchronously
await permissionChecker.applyPermissionsAsync(permissionSet);

// Check if a specific permission is granted
console.log(permissionChecker.checkPermission("orgA.tenant1.abc", "issue")); // Outputs: true/false
```