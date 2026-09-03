// tool-config.js
// 工具配置页面脚本：基础多行输入动态增删 + MAP 映射组结构动态增删 + 提交校验
(function() {
    document.addEventListener('DOMContentLoaded', function() {
        var form = document.querySelector('.config-form');
        if (form) {
            // 事件委托：同时覆盖动态添加的输入元素
            form.addEventListener('focusout', function(e) {
                if (e.target.matches('input, select, textarea')) {
                    validateField(e.target);
                }
            });
            form.addEventListener('input', function(e) {
                if (e.target.matches('input, select, textarea')) {
                    clearError(e.target);
                }
            });
        }
    });
})();

/**
 * 解析多行输入容器的名称前缀：优先取 data-name-prefix，否则从首个输入的 name 去掉行号后缀。
 * 兼容基础类型（config_key_N）与 MAP 组内子配置（config_itemKey_groupIdx_valueKey_N）。
 */
function getMultiNamePrefix(multiContainer) {
    if (multiContainer.dataset.namePrefix) {
        return multiContainer.dataset.namePrefix;
    }
    var firstInput = multiContainer.querySelector('input');
    if (firstInput && firstInput.name) {
        return firstInput.name.replace(/_\d+$/, '');
    }
    return null;
}

function addMultiRow(btn) {
    var multiContainer = btn.closest('.config-multi-input');
    var row = btn.closest('.multi-input-row');
    var firstInput = row ? row.querySelector('input') : multiContainer.querySelector('input');
    var isPassword = firstInput ? firstInput.type === 'password' : false;
    var prefix = getMultiNamePrefix(multiContainer);
    if (!prefix) {
        return;
    }
    var errKey = multiContainer.dataset.key || '';

    var newRow = document.createElement('div');
    newRow.className = 'multi-input-row';
    newRow.innerHTML =
        '<input type="' + (isPassword ? 'password' : 'text') +
        '" name="' + prefix + '_' + multiContainer.querySelectorAll('.multi-input-row').length + '"' +
        ' class="config-input multi-input-field"' +
        (errKey ? ' data-key="' + errKey + '"' : '') + '>' +
        '<button type="button" class="btn-remove-row" onclick="removeMultiRow(this)">-</button>' +
        '<button type="button" class="btn-add-row" onclick="addMultiRow(this)">+</button>';

    // 移除当前行的 + 按钮，只保留新行的 + 按钮
    btn.remove();
    multiContainer.appendChild(newRow);
    var newInput = newRow.querySelector('input');
    if (newInput) {
        newInput.focus();
    }
}

function removeMultiRow(btn) {
    var multiContainer = btn.closest('.config-multi-input');
    var rows = multiContainer.querySelectorAll('.multi-input-row');
    if (rows.length <= 1) {
        return;
    }
    btn.closest('.multi-input-row').remove();

    var prefix = getMultiNamePrefix(multiContainer);
    if (!prefix) {
        return;
    }
    // 重新编号，并保证仅最后一行有 + 按钮
    var remainRows = multiContainer.querySelectorAll('.multi-input-row');
    remainRows.forEach(function(r, index) {
        var input = r.querySelector('input');
        if (input) {
            input.name = prefix + '_' + index;
        }
        r.querySelectorAll('.btn-add-row').forEach(function(b) { b.remove(); });
        if (index === remainRows.length - 1) {
            var addBtn = document.createElement('button');
            addBtn.type = 'button';
            addBtn.className = 'btn-add-row';
            addBtn.textContent = '+';
            addBtn.onclick = function() { addMultiRow(this); };
            r.appendChild(addBtn);
        }
    });
}

/**
 * MAP 结构：新增一组。克隆页面内空组模板（TPLIDX 为组序号占位）并替换为实际序号。
 * 组序号单调递增（nextIdx），删除组后不重排，由后端按序号排序；序号唯一性由 nextIdx 保证。
 */
function addMapGroup(btn) {
    var container = btn.closest('.config-map-input');
    var itemKey = container.dataset.key;
    var tpl = document.getElementById('mapTemplate_' + itemKey);
    if (!tpl) {
        return;
    }
    var nextIdx = parseInt(container.dataset.nextIdx || '0', 10);
    if (isNaN(nextIdx)) {
        nextIdx = 0;
    }
    container.dataset.nextIdx = String(nextIdx + 1);

    var clone = tpl.content.cloneNode(true);
    clone.querySelectorAll('*').forEach(function(el) {
        ['name', 'id', 'data-key', 'data-name-prefix'].forEach(function(attr) {
            var v = el.getAttribute(attr);
            if (v && v.indexOf('TPLIDX') !== -1) {
                el.setAttribute(attr, v.split('TPLIDX').join(String(nextIdx)));
            }
        });
    });
    container.insertBefore(clone, btn);

    var groups = container.querySelectorAll(':scope > .map-group');
    var last = groups[groups.length - 1];
    if (last) {
        var keyInput = last.querySelector('.map-key-input');
        if (keyInput) {
            keyInput.focus();
        }
    }
}

/**
 * MAP 结构：删除一组（序号不重排，仅更新显示序号）
 */
function removeMapGroup(btn) {
    var group = btn.closest('.map-group');
    var container = btn.closest('.config-map-input');
    if (!group || !container) {
        return;
    }
    group.remove();
    container.querySelectorAll(':scope > .map-group').forEach(function(g, i) {
        var title = g.querySelector('.map-group-title');
        if (title) {
            title.textContent = '组 ' + (i + 1);
        }
    });
}

/**
 * MAP 结构组名校验：非空、不含英文逗号/冒号、不重复
 */
function validateMapKeys() {
    var valid = true;
    document.querySelectorAll('.config-map-input').forEach(function(container) {
        var seen = {};
        container.querySelectorAll(':scope > .map-group').forEach(function(group) {
            var input = group.querySelector('.map-key-input');
            if (!input) {
                return;
            }
            var errDiv = group.querySelector('.map-key-error');
            var value = input.value.trim();
            var msg = '';
            if (!value) {
                msg = '组名称不能为空';
            } else if (/[,]/.test(value) || /[:]/.test(value)) {
                msg = '组名称不能包含英文逗号或冒号';
            } else if (seen[value]) {
                msg = '组名称重复：' + value;
            }
            seen[value] = true;
            if (errDiv) {
                if (msg) {
                    errDiv.textContent = msg;
                    errDiv.style.display = 'block';
                    input.classList.add('invalid');
                    valid = false;
                } else {
                    errDiv.style.display = 'none';
                    input.classList.remove('invalid');
                }
            }
        });
    });
    return valid;
}

function validateField(field) {
    var key = field.dataset.key;
    if (!key) {
        var container = field.closest('.config-multi-input');
        if (container) {
            key = container.dataset.key;
        }
    }

    if (!key) {
        return true;
    }

    var errorDiv = document.getElementById('error-' + key);
    if (!errorDiv) {
        return true;
    }

    if (!field.checkValidity()) {
        errorDiv.textContent = field.validationMessage || field.title || '格式不正确';
        errorDiv.style.display = 'block';
        field.classList.add('invalid');
        return false;
    }

    errorDiv.style.display = 'none';
    field.classList.remove('invalid');
    return true;
}

function clearError(field) {
    var key = field.dataset.key;
    if (!key) {
        var container = field.closest('.config-multi-input');
        if (container) {
            key = container.dataset.key;
        }
    }

    if (!key) {
        return;
    }

    var errorDiv = document.getElementById('error-' + key);
    if (errorDiv) {
        errorDiv.style.display = 'none';
    }
    field.classList.remove('invalid');
}

function validateForm(event) {
    event.preventDefault();
    var form = document.getElementById('configForm');
    var isValid = true;

    var inputs = form.querySelectorAll('input:not([type="button"]):not([type="submit"]), select, textarea');
    inputs.forEach(function(input) {
        if (!validateField(input)) {
            isValid = false;
        }
    });

    // MAP 结构组名校验
    if (!validateMapKeys()) {
        isValid = false;
    }

    if (isValid) {
        form.submit();
    }

    return isValid;
}
