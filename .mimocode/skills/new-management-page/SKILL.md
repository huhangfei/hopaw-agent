---
name: new-management-page
description: Use when creating a new management page (CRUD list + detail view) in the HoPaw Agent admin UI. Covers the full stack: Controller, Service, HTML template, JavaScript, CSS, and sidebar menu registration. Follows the established Thymeleaf + ThymeLeaf layout pattern.
---

# New Management Page — Full-Stack Workflow

## Overview

When the user asks to "add a new page", "create a management page for X", or "build a list page for Y", follow this checklist. Every new page touches the same 7 areas in a fixed order.

**Announce at start:** "I'm using the new-management-page skill to create the management page."

## Project Structure

- **Controllers**: `hopaw-app/src/main/java/com/agent/hopaw/controller/`
- **Services**: `hopaw-infra/src/main/java/com/agent/hopaw/infra/service/`
- **HTML templates**: `hopaw-app/src/main/resources/templates/`
- **JavaScript**: `hopaw-app/src/main/resources/static/js/`
- **CSS**: `hopaw-app/src/main/resources/static/css/page/`
- **Layout**: `hopaw-app/src/main/resources/templates/layouts/default.html`

## The 7-Step Checklist

### Step 1: Service Layer

File: `hopaw-infra/.../service/{Entity}Service.java`

Create or extend the service with CRUD methods:

```java
@Service
public class {Entity}Service {
    private final {Entity}Mapper mapper;

    public {Entity}Service({Entity}Mapper mapper) {
        this.mapper = mapper;
    }

    public List<{Entity}> findAll() { return mapper.findAll(); }
    public {Entity} findById(Long id) { return mapper.findById(id); }
    public {Entity} create({Entity} entity) { mapper.insert(entity); return entity; }
    public void update({Entity} entity) { mapper.update(entity); }
    public void delete(Long id) { mapper.deleteById(id); }
}
```

If the entity doesn't exist yet, also create the Mapper interface and XML (see add-entity-field skill for XML patterns).

### Step 2: Controller

File: `hopaw-app/.../controller/{Entity}Controller.java`

```java
@Controller
@RequestMapping("/{entity-name}")
public class {Entity}Controller {
    private final {Entity}Service service;

    public {Entity}Controller({Entity}Service service) {
        this.service = service;
    }

    // Page route
    @GetMapping
    public String page(Model model) {
        model.addAttribute("items", service.findAll());
        model.addAttribute("activePage", "{entity-name}");
        return "{entity-name}";
    }

    // API routes
    @GetMapping("/api/list")
    @ResponseBody
    public List<{Entity}> list() {
        return service.findAll();
    }

    @PostMapping("/api/add")
    @ResponseBody
    public {Entity} add(@RequestBody {Entity} entity) {
        return service.create(entity);
    }

    @PostMapping("/api/update")
    @ResponseBody
    public void update(@RequestBody {Entity} entity) {
        service.update(entity);
    }

    @PostMapping("/api/delete")
    @ResponseBody
    public void delete(@RequestBody Map<String, Long> body) {
        service.delete(body.get("id"));
    }
}
```

Key conventions:
- `activePage` must match the sidebar menu `th:classappend` value
- API routes use `/api/` prefix
- Use `@ResponseBody` for JSON endpoints
- Use `@RequestBody` for POST with JSON body

### Step 3: HTML Template

File: `hopaw-app/.../templates/{entity-name}.html`

```html
<!DOCTYPE html>
<html xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      xmlns:th="http://www.thymeleaf.org"
      layout:decorate="~{layouts/default}">
<head>
    <title>{页面标题} - HoPaw</title>
</head>
<body>
<div layout:fragment="pageTitle">{页面标题}</div>

<main layout:fragment="content" class="{entity-name}-content">
    <div class="page-header">
        <h2>{页面标题}</h2>
        <button class="btn btn-primary" onclick="openAddModal()">+ 新增</button>
    </div>

    <div class="table-container">
        <table class="data-table">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>名称</th>
                    <!-- more columns -->
                    <th>操作</th>
                </tr>
            </thead>
            <tbody id="tableBody">
                <!-- rendered by JS -->
            </tbody>
        </table>
    </div>
</main>

<!-- Add/Edit Modal -->
<div id="modal" class="modal-overlay" style="display:none;">
    <div class="modal-content">
        <div class="modal-header">
            <h3 id="modalTitle">新增</h3>
            <button class="modal-close" onclick="closeModal()">&times;</button>
        </div>
        <div class="modal-body">
            <input type="hidden" id="editId">
            <div class="form-row">
                <div class="form-group">
                    <label>名称</label>
                    <input type="text" id="inputName" placeholder="请输入名称">
                </div>
            </div>
            <!-- more fields -->
        </div>
        <div class="modal-footer">
            <button class="btn btn-secondary" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="submitForm()">确定</button>
        </div>
    </div>
</div>

<th:block layout:fragment="extraCss">
    <link rel="stylesheet" th:href="@{/css/page/{entity-name}.css}">
</th:block>

<th:block layout:fragment="extraScripts">
    <script th:src="@{/js/{entity-name}.js}"></script>
</th:block>
</body>
</html>
```

### Step 4: JavaScript

File: `hopaw-app/.../static/js/{entity-name}.js`

```javascript
// Load data
function loadData() {
    fetch('/{entity-name}/api/list')
        .then(r => r.json())
        .then(data => renderTable(data));
}

function renderTable(items) {
    const tbody = document.getElementById('tableBody');
    tbody.innerHTML = items.map(item => `
        <tr>
            <td>${item.id}</td>
            <td>${item.name}</td>
            <!-- more columns -->
            <td class="actions">
                <button class="btn btn-sm" onclick="openEditModal(${item.id})">编辑</button>
                <button class="btn btn-sm btn-danger" onclick="deleteItem(${item.id})">删除</button>
            </td>
        </tr>
    `).join('');
}

// Add
function openAddModal() {
    document.getElementById('modalTitle').textContent = '新增';
    document.getElementById('editId').value = '';
    document.getElementById('inputName').value = '';
    document.getElementById('modal').style.display = 'flex';
}

// Edit
function openEditModal(id) {
    fetch(`/{entity-name}/api/list`)
        .then(r => r.json())
        .then(items => {
            const item = items.find(i => i.id === id);
            if (!item) return;
            document.getElementById('modalTitle').textContent = '编辑';
            document.getElementById('editId').value = item.id;
            document.getElementById('inputName').value = item.name;
            document.getElementById('modal').style.display = 'flex';
        });
}

// Submit
function submitForm() {
    const id = document.getElementById('editId').value;
    const body = {
        name: document.getElementById('inputName').value,
        // ... more fields
    };
    const url = id ? '/{entity-name}/api/update' : '/{entity-name}/api/add';
    if (id) body.id = parseInt(id);

    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(() => {
        closeModal();
        loadData();
    });
}

// Delete
function deleteItem(id) {
    if (!confirm('确定删除？')) return;
    fetch('/{entity-name}/api/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: id })
    }).then(() => loadData());
}

// Close modal
function closeModal() {
    document.getElementById('modal').style.display = 'none';
}

// Init
document.addEventListener('DOMContentLoaded', loadData);
```

### Step 5: CSS

File: `hopaw-app/.../static/css/page/{entity-name}.css`

```css
.{entity-name}-content {
    padding: 20px;
}

.page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.table-container {
    background: var(--bg-primary, #fff);
    border-radius: 8px;
    overflow: hidden;
}

.data-table {
    width: 100%;
    border-collapse: collapse;
}

.data-table th,
.data-table td {
    padding: 12px 16px;
    text-align: left;
    border-bottom: 1px solid var(--border-color, #eee);
}

.data-table th {
    background: var(--bg-secondary, #f5f5f5);
    font-weight: 600;
}

.actions {
    display: flex;
    gap: 4px;
}

.btn-sm {
    padding: 4px 8px;
    font-size: 12px;
}
```

### Step 6: Sidebar Menu Registration

File: `hopaw-app/.../templates/layouts/default.html`

Add a new `<a>` tag in the `<nav class="menu-sidebar">` section:

```html
<a href="/{entity-name}" class="menu-btn" th:classappend="${activePage == '{entity-name}' ? 'active' : ''}" title="{页面标题}">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <!-- SVG icon path -->
    </svg>
    <span class="menu-label">{菜单名称}</span>
</a>
```

### Step 7: Database Table (if new entity)

If the entity is brand new, also update `DatabaseInitializer.java`:

```java
stmt.execute("CREATE TABLE IF NOT EXISTS {table_name} (" +
    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
    "name TEXT NOT NULL, " +
    // ... columns
    ")");
```

## Verification

After all changes:
1. Page loads at `/{entity-name}` without errors
2. Table renders with data from API
3. Add modal opens, form submits, new record appears
4. Edit modal pre-fills data, updates on submit
5. Delete works with confirmation
6. Sidebar link is active when on the page
7. CSS styles match existing pages (dark theme support)

## Common Patterns

- **Left-right layout** (like tools page): Use a two-column container with a list on left and detail on right
- **Tab-based layout** (like settings page): Use `settings.html` pattern with sub-templates
- **Modal for add/edit**: Standard pattern shown above
- **Inline editing**: Use contenteditable or input fields directly in the table

## Remember

- Always use `layout:decorate="~{layouts/default}"` for Thymeleaf layout
- `activePage` must match the sidebar `th:classappend` value exactly
- API routes use `/api/` prefix, page routes don't
- Use `@ResponseBody` for JSON endpoints
- Include both `extraCss` and `extraScripts` fragments
- Follow existing CSS variable patterns (`--bg-primary`, `--border-color`, etc.)
