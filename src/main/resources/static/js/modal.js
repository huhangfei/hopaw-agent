var Modal = (function() {
    var initialized = false;

    function init() {
        if (initialized) return;
        initialized = true;

        document.addEventListener('click', function(e) {
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
