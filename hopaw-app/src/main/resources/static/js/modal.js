var Modal = (function() {
    var initialized = false;

    function init() {
        if (initialized) return;
        initialized = true;

        // 用 mousedown 而非 click：click 的 target 取决于 mouseup 位置，
        // 在窗体内按下并拖到遮罩释放会误触发关闭。mousedown 的 target 即按下位置。
        document.addEventListener('mousedown', function(e) {
            if (e.target.matches('.modal-overlay.active')) {
                e.target.classList.remove('active');
            }
        });

        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                var activeModals = document.querySelectorAll('.modal-overlay.active');
                if (activeModals.length > 0) {
                    activeModals[activeModals.length - 1].classList.remove('active');
                }
            }
        });
    }

    return {
        open: function(id) {
            init();
            var el = document.getElementById(id);
            if (el) el.classList.add('active');
        },
        close: function(id) {
            var el = document.getElementById(id);
            if (el) el.classList.remove('active');
        }
    };
})();
