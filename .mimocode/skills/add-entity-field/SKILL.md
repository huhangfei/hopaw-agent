---
name: add-entity-field
description: Use when adding a new field/column to an existing entity (Agent, AiModel, AiModelProvider, ScheduledTask, etc.) across the full stack — model class, MyBatis mapper XML, DatabaseInitializer, Controller, Service, HTML template, and JavaScript. This is the primary repeated workflow for expanding entity schemas.
---

# Add Entity Field — Full-Stack Workflow

## Overview

When the user asks to "add a field to X", "add a column to Y", or similar, follow this checklist systematically. Every field addition touches the same 8 files in a fixed order.

**Announce at start:** "I'm using the add-entity-field skill to add the new field."

## Project Structure

- **Model entities**: `hopaw-contract/src/main/java/com/agent/hopaw/infra/model/entity/`
- **MyBatis mappers (XML)**: `hopaw-infra/src/main/resources/mapper/`
- **DatabaseInitializer**: `hopaw-app/src/main/java/com/agent/hopaw/config/DatabaseInitializer.java`
- **Services**: `hopaw-infra/src/main/java/com/agent/hopaw/infra/service/`
- **Controllers**: `hopaw-app/src/main/java/com/agent/hopaw/controller/` (or `hopaw-plugin-repo/...`)
- **HTML templates**: `hopaw-app/src/main/resources/templates/`
- **JavaScript**: `hopaw-app/src/main/resources/static/js/`
- **CSS**: `hopaw-app/src/main/resources/static/css/page/`

## The 8-Step Checklist

### Step 1: Java Model Entity

File: `hopaw-contract/.../model/entity/{Entity}.java`

1. Add the field declaration after existing fields:
```java
private String newFieldName;
```

2. Add getter/setter at the end of the class:
```java
public String getNewFieldName() {
    return newFieldName;
}

public void setNewFieldName(String newFieldName) {
    this.newFieldName = newFieldName;
}
```

3. If the entity has constructors that set defaults, add the new field with a sensible default.

### Step 2: MyBatis Mapper XML

File: `hopaw-infra/src/main/resources/mapper/{Entity}Mapper.xml`

Update **all 4 sections**:

1. **resultMap** — add column mapping:
```xml
<result property="newFieldName" column="new_field_name"/>
```

2. **SELECT queries** — add column name to ALL SELECT statements:
```sql
SELECT id, ..., new_field_name FROM table_name
```

3. **INSERT** — add column and value:
```sql
INSERT INTO table_name (..., new_field_name) VALUES (..., #{newFieldName})
```

4. **UPDATE** — add to SET clause:
```sql
SET ..., new_field_name = #{newFieldName}
WHERE id = #{id}
```

### Step 3: DatabaseInitializer

File: `hopaw-app/.../config/DatabaseInitializer.java`

1. **CREATE TABLE** — add the column definition:
```java
"new_field_name TEXT DEFAULT 'default_value',"
```

2. **ALTER TABLE for existing DBs** — add migration near other ALTER TABLE statements:
```java
try {
    stmt.execute("ALTER TABLE table_name ADD COLUMN new_field_name TEXT DEFAULT 'default_value'");
} catch (Exception e) {
    // Column may already exist
}
```

### Step 4: Controller

File: `hopaw-app/.../controller/{Entity}Controller.java`

1. Add parameter to `create` / `insert` method signature (if using form params)
2. Add parameter to `update` method signature
3. Set the field on the entity object:
```java
entity.setNewFieldName(newFieldName);
```

### Step 5: Service

File: `hopaw-infra/.../service/{Entity}Service.java`

1. In `createEntity()` — add default value logic:
```java
if (entity.getNewFieldName() == null) {
    entity.setNewFieldName("defaultValue");
}
```

2. In `updateEntity()` — add to the copy/update block:
```java
existing.setNewFieldName(entity.getNewFieldName() != null ? entity.getNewFieldName() : existing.getNewFieldName());
```

### Step 6: HTML Template

File: `hopaw-app/.../templates/{page}.html`

1. **Add modal** — add form field after existing fields:
```html
<div class="form-row">
    <div class="form-group">
        <label>New Field Name</label>
        <input type="text" id="addNewFieldName" placeholder="Description">
    </div>
</div>
```

2. **Edit modal** — add the same field with `edit` prefix:
```html
<input type="text" id="editNewFieldName" placeholder="Description">
```

3. Update the `onclick` handler of add/edit buttons to pass the new field:
```html
onclick="addEntity('addName', 'addDescription', ..., 'addNewFieldName')"
```

### Step 7: JavaScript

File: `hopaw-app/.../static/js/{page}.js`

1. In `addEntity()` / `submitAdd()` — read the new field:
```javascript
const newFieldName = document.getElementById('addNewFieldName').value;
```

2. Add to the request body:
```javascript
body: JSON.stringify({ ..., newFieldName: newFieldName })
```

3. In `editEntity()` / `submitEdit()` — same pattern with `edit` prefix:
```javascript
const newFieldName = document.getElementById('editNewFieldName').value;
```

4. In `loadEntity()` / `populateEditForm()` — set the value:
```javascript
document.getElementById('editNewFieldName').value = entity.newFieldName || '';
```

### Step 8: CSS (if needed)

File: `hopaw-app/.../static/css/page/{page}.css`

If the new field needs special styling (dropdown, textarea, etc.), add CSS rules here. Follow existing patterns in the file.

## Verification

After all changes:
1. Check that every SELECT in the XML includes the new column
2. Check that INSERT and UPDATE both reference the new field
3. Check that the ALTER TABLE migration is wrapped in try/catch
4. Check that both add and edit modals have the field
5. Check that the JS submit functions read and send the field

## Common Variations

- **Boolean fields** — use `INTEGER DEFAULT 0` in SQL, `Boolean` in Java, checkbox in HTML
- **Integer fields** — use `INTEGER DEFAULT N` in SQL, `Integer` in Java, number input in HTML
- **Text/JSON fields** — use `TEXT` in SQL, `String` in Java, textarea in HTML
- **Enum fields** — use `TEXT` in SQL, `String` in Java, `<select>` dropdown in HTML

## Remember

- Always update ALL SELECT statements in the XML (there are usually 4-6)
- The ALTER TABLE must be wrapped in try/catch for idempotency
- Both add AND edit modals need the field
- Default values go in the Service layer, not just the DB
