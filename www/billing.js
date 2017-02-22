var exec = require('cordova/exec');

module.exports = {
  getProduct: function (productId, productType) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'getProduct', [productId, productType]);
      }
    );

  },
  getPurchases: function (productType) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'getPurchases', [productType]);
      }
    );
  },
  makePurchase: function (productId, productType) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'makePurchase', [productId, productType]);
      }
    );
  }
};
