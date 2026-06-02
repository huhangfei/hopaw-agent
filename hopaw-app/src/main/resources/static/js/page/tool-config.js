// tool-main.js
// 工具配置页面脚本
(function() {
    document.addEventListener('DOMContentLoaded', function() {
        var form = document.querySelector('.config-form');
        if (form) {
            var inputs = form.querySelectorAll('input, select, textarea');
            inputs.forEach(function(input) {
                input.addEventListener('blur', function() {
                    validateField(this);
                });
                input.addEventListener('input', function() {
                    clearError(this);
                });
            });
        }
    });
})();

function addMultiRow(btn) {
    var multiContainer = btn.closest('.config-multi-input');
    var rows = multiContainer.querySelectorAll('.multi-input-row');
    var key = '';
    var isPassword = false;
    
    var firstInput = multiContainer.querySelector('input');
    if (firstInput) {
        var name = firstInput.name;
        var match = name.match(/config_([^_]+)_/);
        if (match) {
            key = match[1];
        }
        isPassword = firstInput.type === 'password';
    }

    var newRow = document.createElement('div');
    newRow.className = 'multi-input-row';
    newRow.innerHTML = `
        <input type="${isPassword ? 'password' : 'text'}" name="config_${key}_${rows.length}" class="config-input multi-input-field">
        <button type="button" class="btn-remove-row" onclick="removeMultiRow(this)">-</button>
        <button type="button" class="btn-add-row" onclick="addMultiRow(this)">+</button>
    `;
    
    var newInput = newRow.querySelector('input');
    newInput.addEventListener('blur', function() {
        validateField(this);
    });
    newInput.addEventListener('input', function() {
        clearError(this);
    });
    
    // 移除当前行的 + 按钮，只保留新行的 + 按钮
    btn.remove();
    
    multiContainer.appendChild(newRow);
}

function removeMultiRow(btn) {
    var row = btn.closest('.multi-input-row');
    var multiContainer = btn.closest('.config-multi-input');
    var rows = multiContainer.querySelectorAll('.multi-input-row');
    
    if (rows.length > 1) {
        var wasLast = btn.nextElementSibling && btn.nextElementSibling.classList.contains('btn-add-row');
        row.remove();
        
        // 重新排序所有输入框的 name
        var key = '';
        var firstInput = multiContainer.querySelector('input');
        if (firstInput) {
            var name = firstInput.name;
            var match = name.match(/config_([^_]+)_/);
            if (match) {
                key = match[1];
            }
        }
        
        var newRows = multiContainer.querySelectorAll('.multi-input-row');
        newRows.forEach(function(r, index) {
            var input = r.querySelector('input');
            if (input) {
                input.name = 'config_' + key + '_' + index;
            }
            
            // 确保最后一行有 + 按钮
            if (index === newRows.length - 1) {
                var existingAddBtn = r.querySelector('.btn-add-row');
                if (!existingAddBtn) {
                    var newAddBtn = document.createElement('button');
                    newAddBtn.type = 'button';
                    newAddBtn.className = 'btn-add-row';
                    newAddBtn.onclick = function() { addMultiRow(this); };
                    newAddBtn.textContent = '+';
                    r.appendChild(newAddBtn);
                }
            }
        });
    }
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
    
    if (isValid) {
        form.submit();
    }
    
    return isValid;
}
