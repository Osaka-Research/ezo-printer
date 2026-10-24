package com.osakaresearch.ezoprint

enum class TagTemplate(val displayName: String, val hint1: String, val hint2: String, val hint3: String) {
    PRODUCT("Product Tag",  "Product Name",  "Price (e.g. \$9.99)", "SKU / Barcode"),
    PRICE  ("Price Label",  "Product Name",  "Price (large)",       "Note / Weight"),
    NAME   ("Name Tag",     "Name (large)",  "Sub-title",           ""),
    CUSTOM ("Custom",       "Line 1",        "Line 2",              "Line 3")
}
