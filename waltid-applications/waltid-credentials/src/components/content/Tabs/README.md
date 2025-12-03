# Tabs Component

The tabs component supports up to 6 tabs. If the name for the tab is not provided in the tabNames array,
the tab won't show up.

### Props
```json
{
  "tabNames": {
    "type": "Array",
    "default": []
  }
}
```

### Usage Example

```
::tabs{:tabNames='["Tab1Name", "Tab2Name", "Tab3Name"]'}
#tab1
 
... tab 1 content ...

#tab2

... tab 2 content ...

#tab3

... tab 3 content ...
::

```
