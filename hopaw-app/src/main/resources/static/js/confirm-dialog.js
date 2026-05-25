function showConfirm(message) {
    return new Promise(function(resolve) {
        var overlay = document.createElement('div');
        overlay.className = 'confirm-overlay';
        overlay.innerHTML =
            '<div class="confirm-dialog">' +
                '<div class="confirm-body">' +
                    '<svg class="confirm-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"/></svg>' +
                    '<div class="confirm-text">' + message + '</div>' +
                '</div>' +
                '<div class="confirm-footer">' +
                    '<button class="btn-cancel confirm-btn">取消</button>' +
                    '<button class="btn-submit confirm-btn confirm-ok">确定</button>' +
                '</div>' +
            '</div>';

        document.body.appendChild(overlay);

        overlay.querySelector('.btn-cancel').addEventListener('click', function() {
            document.body.removeChild(overlay);
            resolve(false);
        });

        overlay.querySelector('.confirm-ok').addEventListener('click', function() {
            document.body.removeChild(overlay);
            resolve(true);
        });

        overlay.addEventListener('click', function(e) {
            if (e.target === overlay) {
                document.body.removeChild(overlay);
                resolve(false);
            }
        });
    });
}

function showConfirmWithCheckbox(message, checkboxLabel, defaultChecked) {
    return new Promise(function(resolve) {
        var overlay = document.createElement('div');
        overlay.className = 'confirm-overlay';
        var checkboxHtml = checkboxLabel ?
            '<label class="confirm-checkbox">' +
                '<input type="checkbox" id="confirmCheckbox"' + (defaultChecked ? ' checked' : '') + '>' +
                '<span>' + checkboxLabel + '</span>' +
            '</label>' : '';
        overlay.innerHTML =
            '<div class="confirm-dialog">' +
                '<div class="confirm-body">' +
                    '<svg class="confirm-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"/></svg>' +
                    '<div class="confirm-text">' + message + '</div>' +
                    checkboxHtml +
                '</div>' +
                '<div class="confirm-footer">' +
                    '<button class="btn-cancel confirm-btn">取消</button>' +
                    '<button class="btn-submit confirm-btn confirm-ok">确定</button>' +
                '</div>' +
            '</div>';

        document.body.appendChild(overlay);

        overlay.querySelector('.btn-cancel').addEventListener('click', function() {
            document.body.removeChild(overlay);
            resolve({ confirmed: false, checked: false });
        });

        overlay.querySelector('.confirm-ok').addEventListener('click', function() {
            var checked = false;
            var checkbox = document.getElementById('confirmCheckbox');
            if (checkbox) {
                checked = checkbox.checked;
            }
            document.body.removeChild(overlay);
            resolve({ confirmed: true, checked: checked });
        });

        overlay.addEventListener('click', function(e) {
            if (e.target === overlay) {
                document.body.removeChild(overlay);
                resolve({ confirmed: false, checked: false });
            }
        });
    });
}
