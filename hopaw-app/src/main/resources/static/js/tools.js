// Tool management page
function selectToolSet(el) {
    var index = el.getAttribute('data-index');

    // left panel: remove active from all, add to clicked
    var items = document.querySelectorAll('.tool-list-item');
    items.forEach(function(item) { item.classList.remove('active'); });
    el.classList.add('active');

    // right panel: hide all details, show selected
    var details = document.querySelectorAll('.tool-detail');
    details.forEach(function(d) { d.classList.remove('active'); });
    var target = document.querySelector('.tool-detail[data-index="' + index + '"]');
    if (target) target.classList.add('active');
}
