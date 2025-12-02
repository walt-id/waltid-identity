# Alert Component

The alert component is a flexible callout box that can be used to draw the user's attention.

### Props

```json
{
  "type": {
    "type": "String",
    "default": "Info" // other options "Success" or "Error"
  }
}
```

Usage Example
Mark up your content as follows to use the Alert component. The 'type' attribute can be set to 'Success', 'Error', or '
Info'. If it is omitted, 'Info' is used by default.

```markdown
::callout{type="Error"}
Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard
dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen
book. It has survived not only five centuries,
::

Replace "Error" with any accepted type of alert:

::callout{type="Success"}
...your message here...
::

::callout{type="Info"}
...your message here...
::

::callout{type="Error"}
...your message here...
::

If the 'type' attribute is not provided, 'Info' is used by default:

::callout
...your default Info message here...
::
```

In all of these cases, the component will apply an appropriate border color and display a relevant icon based on the '
type' attribute. The content of the message goes in between the opening and closing "::".
