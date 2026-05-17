function showConfirm(message) {
    return new Promise(function(resolve) {
        var overlay = document.createElement('div');
        overlay.className = 'confirm-overlay';
        overlay.innerHTML =
            '<div class="confirm-dialog">' +
                '<div class="confirm-body">' +
                    '<svg class="confirm-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>' +
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
