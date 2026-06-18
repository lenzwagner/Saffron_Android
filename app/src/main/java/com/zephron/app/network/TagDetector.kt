package com.zephron.app.network

object TagDetector {

    val ALL_TAGS = listOf(
        // Gang (Pflichtfeld)
        "Vorspeise", "Hauptgang", "Dessert", "Getränk",
        // Proteine
        "Hähnchen", "Pute", "Rind", "Schwein", "Fisch", "Meeresfrüchte", "Lamm", "Ente",
        // Kohlenhydrate
        "Reis", "Pasta", "Nudeln", "Kartoffeln", "Brot",
        // Ernährung
        "Fleisch", "Vegetarisch", "Vegan",
        // Temperatur
        "Warm", "Kalt",
        // Mahlzeitentyp
        "Suppe", "Salat", "Süß", "Frühstück",
        // Küche
        "Italienisch", "Asiatisch", "Chinesisch", "Japanisch", "Thailändisch",
        "Indisch", "Mexikanisch", "Mediterran", "Französisch", "Griechisch",
        "Türkisch", "Amerikanisch", "Deutsch", "Spanisch", "Koreanisch", "Vietnamesisch"
    )

    fun detect(title: String, description: String): List<String> {
        val text = (title + " " + description).lowercase()
        val tags = mutableListOf<String>()

        if (text.any("chicken", "poultry", "hen", "wings", "drumstick", "hähnchen", "huhn", "hühnchen", "geflügel"))
            tags += "Hähnchen"
        if (text.any("turkey", "pute", "truthahn", "putenbrust", "putenfilet", "putenschnitzel", "putenbrust"))
            tags += "Pute"
        if (text.any("duck", "ente", "entenbrust", "entenkeule", "entenfilet"))
            tags += "Ente"
        if (text.any("goose", "gans"))
            tags += "Hähnchen"
        if (text.any("beef", "steak", "brisket", "burger", "mince", "ground beef", "short rib", "rind", "rindfleisch"))
            tags += "Rind"
        if (text.any("pork", "bacon", "ham", "sausage", "chorizo", "prosciutto", "pancetta", "ribs", "pulled pork", "carnitas", "schwein", "schweinefleisch", "speck", "schinken", "wurst"))
            tags += "Schwein"
        if (text.any("salmon", "tuna", "cod", "fish", "trout", "tilapia", "halibut", "sea bass", "anchovy", "lachs", "thunfisch", "forelle", "kabeljau", "fisch"))
            tags += "Fisch"
        if (text.any("shrimp", "prawn", "lobster", "crab", "seafood", "scallop", "mussel", "clam", "oyster", "squid", "octopus", "garnele", "hummer", "krabbe", "meeresfrüchte", "tintenfisch"))
            tags += "Meeresfrüchte"
        if (text.any("lamb", "mutton", "lamm", "lammfleisch"))
            tags += "Lamm"

        if (text.any("rice", "risotto", "fried rice", "congee", "pilaf", "paella", "reis"))
            tags += "Reis"
        if (text.any("pasta", "spaghetti", "penne", "fettuccine", "linguine", "tagliatelle",
                "carbonara", "lasagna", "lasagne", "ravioli", "gnocchi", "orzo", "nudel"))
            tags += "Pasta"
        if (text.any("noodle", "ramen", "udon", "soba", "pad thai", "pho", "lo mein",
                "chow mein", "vermicelli", "glass noodle", "egg noodle", "nudeln"))
            tags += "Nudeln"
        if (text.any("potato", "fries", "chips", "mashed potato", "roast potato",
                "hash brown", "kartoffel", "pommes", "bratkartoffel"))
            tags += "Kartoffeln"
        if (text.any("bread", "focaccia", "naan", "pita", "sourdough", "baguette",
                "toast", "sandwich", "wrap", "tortilla", "flatbread", "bun", "roll",
                "bagel", "brioche", "ciabatta", "dumpling", "brot", "brötchen"))
            tags += "Brot"

        val hasMeat = hasMeat(text)
        if (hasMeat) {
            tags += "Fleisch"
        } else {
            if (text.any("vegan", "plant-based", "dairy-free", "pflanzlich")) {
                tags += "Vegan"
            } else {
                tags += "Vegetarisch"
            }
        }

        val isCold = text.any("cold", "chilled", "raw", "sashimi", "ceviche",
            "gazpacho", "ice cream", "frozen", "no-bake", "smoothie", "kalt", "roh", "eiscrème")
        if (isCold) tags += "Kalt"

        val isHot = text.any("baked", "roasted", "grilled", "fried", "sautéed", "sauteed",
            "steamed", "boiled", "braised", "barbecue", "bbq", "stir fry", "pan seared",
            "gebacken", "gebraten", "gegrillt", "gekocht", "gedünstet", "anbraten", "erhitzen")
        if (isHot && !isCold) tags += "Warm"
        if (!isCold && !isHot && hasMeat) tags += "Warm"

        if (text.any("soup", "stew", "broth", "chowder", "bisque", "chili", "hot pot",
                "suppe", "eintopf", "brühe", "bouillon"))
            tags += "Suppe"
        if (text.any("salad", "slaw", "poke bowl", "grain bowl", "buddha bowl",
                "salat", "blattsalat"))
            tags += "Salat"
        if (text.any("dessert", "cake", "cookie", "brownie", "sweet", "chocolate",
                "pastry", "muffin", "pie", "tart", "pudding", "cheesecake", "tiramisu",
                "donut", "churro", "crepe", "waffle", "pancake", "kuchen", "kekse",
                "schokolade", "süß", "torte", "gebäck"))
            tags += "Süß"
        if (text.any("breakfast", "brunch", "pancake", "waffle", "egg", "omelette",
                "omelet", "french toast", "granola", "oatmeal", "porridge",
                "frühstück", "ei", "omelett", "rührei", "pfannkuchen", "haferbrei"))
            tags += "Frühstück"

        // ── Küche ─────────────────────────────────────────────────────────────
        // Asian sub-cuisines also add the broad "Asiatisch" tag.
        if (text.any("chinese", "wok", "dim sum", "kung pao", "chow mein", "lo mein",
                "szechuan", "sweet and sour", "spring roll", "chinesisch")) {
            tags += "Chinesisch"; tags += "Asiatisch"
        }
        if (text.any("japanese", "sushi", "ramen", "teriyaki", "miso", "tempura",
                "udon", "matcha", "gyoza", "katsu", "japanisch")) {
            tags += "Japanisch"; tags += "Asiatisch"
        }
        if (text.any("thai", "pad thai", "tom yum", "green curry", "red curry",
                "thailändisch")) {
            tags += "Thailändisch"; tags += "Asiatisch"
        }
        if (text.any("korean", "kimchi", "bibimbap", "bulgogi", "gochujang",
                "koreanisch")) {
            tags += "Koreanisch"; tags += "Asiatisch"
        }
        if (text.any("vietnamese", "pho", "banh mi", "vietnamesisch")) {
            tags += "Vietnamesisch"; tags += "Asiatisch"
        }
        if (text.any("asian", "stir fry", "soy sauce", "asiatisch"))
            tags += "Asiatisch"
        if (text.any("mexican", "taco", "burrito", "quesadilla", "enchilada",
                "salsa", "guacamole", "nachos", "fajita", "mexikanisch"))
            tags += "Mexikanisch"
        if (text.any("italian", "pizza", "risotto", "osso buco", "focaccia",
                "tiramisu", "cannoli", "bruschetta", "caprese", "italienisch"))
            tags += "Italienisch"
        if (text.any("indian", "curry", "tikka", "masala", "dal", "naan",
                "biryani", "samosa", "chutney", "korma", "indisch"))
            tags += "Indisch"
        if (text.any("french", "ratatouille", "quiche", "croissant", "coq au vin",
                "bourguignon", "crêpe", "französisch"))
            tags += "Französisch"
        if (text.any("greek", "feta", "tzatziki", "gyro", "souvlaki", "moussaka",
                "griechisch")) {
            tags += "Griechisch"; tags += "Mediterran"
        }
        if (text.any("turkish", "kebab", "döner", "doner", "baklava", "lahmacun",
                "türkisch"))
            tags += "Türkisch"
        if (text.any("american", "burger", "bbq", "barbecue", "mac and cheese",
                "cheeseburger", "amerikanisch"))
            tags += "Amerikanisch"
        if (text.any("german", "schnitzel", "bratwurst", "sauerkraut", "spätzle",
                "rouladen", "deutsch"))
            tags += "Deutsch"
        if (text.any("spanish", "paella", "tapas", "tortilla española", "spanisch"))
            tags += "Spanisch"
        if (text.any("mediterranean", "hummus", "tahini", "halloumi", "shakshuka",
                "mediterran"))
            tags += "Mediterran"

        // ── Gang (Pflicht – immer genau einer) ───────────────────────────────
        val courseTag = when {
            text.any("smoothie", "cocktail", "mocktail", "juice", "saft", "kaffee",
                     "coffee", "espresso", "latte", "cappuccino", "tea", "tee",
                     "limonade", "lemonade", "shake", "sirup", "milchshake",
                     "energydrink", "getränk", "drink", "shot", "bowle")
                -> "Getränk"
            text.any("dessert", "nachspeise", "nachtisch", "ice cream", "gelato",
                     "eis ", "mousse", "tiramisu", "cheesecake", "panna cotta",
                     "crème brûlée") || "Süß" in tags
                -> "Dessert"
            text.any("vorspeise", "starter", "appetizer", "antipasto",
                     "amuse-bouche", "tapas", "bruschetta", "crostini",
                     "fingerfood", "dip", "vorspeisen")
                -> "Vorspeise"
            else -> "Hauptgang"
        }
        tags += courseTag

        return tags.distinct()
    }

    private fun hasMeat(text: String) = text.any(
        "chicken", "beef", "pork", "bacon", "ham", "sausage", "salmon",
        "tuna", "fish", "shrimp", "prawn", "lamb", "turkey", "duck",
        "steak", "meat", "pepperoni", "chorizo", "crab", "lobster", "anchovy",
        "veal", "venison", "bison", "goose",
        // German
        "lachs", "thunfisch", "hähnchen", "huhn", "hühnchen", "rind", "schwein", "lamm",
        "wurst", "speck", "schinken", "fisch", "garnele", "fleisch",
        "ente", "entenbrust", "entenkeule", "entenfilet",
        "pute", "truthahn", "putenbrust",
        "kalb", "kalbfleisch", "kalbsschnitzel",
        "hirsch", "reh", "wildschwein", "wild",
        "gans", "fasan", "taube",
        "thunfisch", "dorade", "zander", "hecht", "barsch", "makrele",
        "muschel", "tintenfisch", "jakobsmuschel", "hummer", "krabbe"
    )

    private val EN_TO_DE = mapOf(
        // Gang
        "starter" to "Vorspeise", "appetizer" to "Vorspeise", "appetiser" to "Vorspeise",
        "main course" to "Hauptgang", "main dish" to "Hauptgang", "entree" to "Hauptgang",
        "dessert" to "Dessert", "sweet" to "Dessert",
        "drink" to "Getränk", "beverage" to "Getränk", "cocktail" to "Getränk",
        // Protein
        "chicken" to "Hähnchen", "poultry" to "Hähnchen", "hen" to "Hähnchen",
        "turkey" to "Pute", "pute" to "Pute",
        "duck" to "Ente",
        "beef" to "Rind", "steak" to "Rind", "brisket" to "Rind",
        "pork" to "Schwein", "bacon" to "Schwein", "ham" to "Schwein",
        "fish" to "Fisch", "salmon" to "Fisch", "tuna" to "Fisch",
        "seafood" to "Meeresfrüchte", "shrimp" to "Meeresfrüchte",
        "lamb" to "Lamm", "mutton" to "Lamm",
        "meat" to "Fleisch", "veal" to "Fleisch", "venison" to "Fleisch",
        // Kohlenhydrate
        "rice" to "Reis", "risotto" to "Reis",
        "pasta" to "Pasta", "spaghetti" to "Pasta", "noodles" to "Nudeln",
        "potatoes" to "Kartoffeln", "potato" to "Kartoffeln", "fries" to "Kartoffeln",
        "bread" to "Brot", "naan" to "Brot", "flatbread" to "Brot",
        // Ernährung
        "vegetarian" to "Vegetarisch", "veggie" to "Vegetarisch",
        "vegitarisch" to "Vegetarisch", "vegitarian" to "Vegetarisch",
        "vegan" to "Vegan", "plant-based" to "Vegan",
        // Temperatur
        "hot" to "Warm", "warm" to "Warm", "cold" to "Kalt", "chilled" to "Kalt",
        // Typ
        "soup" to "Suppe", "stew" to "Suppe", "broth" to "Suppe",
        "salad" to "Salat", "slaw" to "Salat",
        "sweet" to "Süß", "dessert" to "Süß", "cake" to "Süß",
        "breakfast" to "Frühstück", "brunch" to "Frühstück",
        // Küche
        "asian" to "Asiatisch",
        "chinese" to "Chinesisch",
        "japanese" to "Japanisch", "sushi" to "Japanisch",
        "thai" to "Thailändisch",
        "korean" to "Koreanisch",
        "vietnamese" to "Vietnamesisch",
        "mexican" to "Mexikanisch",
        "italian" to "Italienisch",
        "indian" to "Indisch", "curry" to "Indisch",
        "mediterranean" to "Mediterran",
        "french" to "Französisch",
        "greek" to "Griechisch",
        "turkish" to "Türkisch",
        "american" to "Amerikanisch",
        "german" to "Deutsch",
        "spanish" to "Spanisch"
    )

    fun normalize(tags: List<String>): List<String> =
        tags.map { tag ->
            val lower = tag.lowercase().trim()
            EN_TO_DE[lower] ?: ALL_TAGS.firstOrNull { it.lowercase() == lower } ?: tag
        }.filter { it in ALL_TAGS }.distinct()

    private fun String.any(vararg keywords: String) = keywords.any { this.contains(it) }

    /** Single source of truth for "is this recipe vegetarian?".
     *  Returns true if isVegetarian==true OR the stored tags contain Vegetarisch/Vegan. */
    fun isVeg(isVegetarian: Boolean, tagsJson: String): Boolean {
        if (isVegetarian) return true
        return try {
            val raw = com.google.gson.Gson().fromJson(
                tagsJson, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            ) ?: emptyList<String>()
            normalize(raw).any { it == "Vegetarisch" || it == "Vegan" }
        } catch (e: Exception) { false }
    }
}
