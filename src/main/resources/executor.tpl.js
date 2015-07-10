var page = require('webpage').create();
page.open('%s', function() {
  page.render('%s');
  phantom.exit();
});
