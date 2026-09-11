package com.personal.hammy

data class Category(val name: String, val slug: String)

object Categories {
    val straight = listOf(
        Category("Amateur", "amateur"),
        Category("Anal", "anal"),
        Category("Asian", "asian"),
        Category("BBW", "bbw"),
        Category("Blonde", "blonde"),
        Category("Blowjob", "blowjob"),
        Category("Brunette", "brunette"),
        Category("Creampie", "creampie"),
        Category("Cumshot", "cumshot"),
        Category("Ebony", "ebony"),
        Category("Hardcore", "hardcore"),
        Category("Latina", "latina"),
        Category("Lesbian", "lesbian"),
        Category("MILF", "milf"),
        Category("Teen", "teen"),
        Category("Threesome", "threesome"),
        Category("Japanese", "japanese"),
        Category("Russian", "russian"),
        Category("German", "german"),
        Category("French", "french"),
        Category("Indian", "indian"),
        Category("Mature", "mature"),
        Category("Massage", "massage"),
        Category("Public", "public"),
        Category("Outdoor", "outdoor"),
        Category("Squirting", "squirting"),
        Category("Bondage", "bondage"),
        Category("Casting", "casting"),
        Category("Homemade", "homemade"),
        Category("POV", "pov"),
        Category("Redhead", "redhead"),
        Category("Big Tits", "big-tits"),
        Category("Big Ass", "big-ass"),
        Category("Handjob", "handjob"),
        Category("Facial", "facial"),
        Category("Gangbang", "gangbang"),
        Category("Orgy", "orgy"),
        Category("Stockings", "stockings")
    )

    val gay = listOf(
        Category("Amateur", "amateur"),
        Category("Anal", "anal"),
        Category("Asian", "asian"),
        Category("Bareback", "bareback"),
        Category("Bears", "bears"),
        Category("Black", "black"),
        Category("Blowjob", "blowjob"),
        Category("Daddy", "daddy"),
        Category("Group Sex", "group-sex"),
        Category("Handjob", "handjob"),
        Category("Hardcore", "hardcore"),
        Category("Interracial", "interracial"),
        Category("Latino", "latino"),
        Category("Massage", "massage"),
        Category("Muscle", "muscle"),
        Category("Public", "public"),
        Category("Twinks", "twinks"),
        Category("Unisex", "unisex"),
        Category("Cumshot", "cumshot"),
        Category("Creampie", "creampie"),
        Category("Outdoor", "outdoor"),
        Category("Homemade", "homemade"),
        Category("POV", "pov"),
        Category("Casting", "casting"),
        Category("Bondage", "bondage"),
        Category("Fetish", "fetish"),
        Category("Japanese", "japanese"),
        Category("German", "german"),
        Category("French", "french"),
        Category("Russian", "russian"),
        Category("Indian", "indian"),
        Category("Mature", "mature"),
        Category("Threesome", "threesome"),
        Category("Orgy", "orgy"),
        Category("Big Cock", "big-cock")
    )

    val trans = listOf(
        Category("Amateur", "amateur"),
        Category("Anal", "anal"),
        Category("Asian", "asian"),
        Category("Blowjob", "blowjob"),
        Category("Brunette", "brunette"),
        Category("Blonde", "blonde"),
        Category("Creampie", "creampie"),
        Category("Cumshot", "cumshot"),
        Category("Ebony", "ebony"),
        Category("Hardcore", "hardcore"),
        Category("Latina", "latina"),
        Category("Lesbian", "lesbian"),
        Category("MILF", "milf"),
        Category("Teen", "teen"),
        Category("Threesome", "threesome"),
        Category("Japanese", "japanese"),
        Category("Russian", "russian"),
        Category("German", "german"),
        Category("French", "french"),
        Category("Indian", "indian"),
        Category("Mature", "mature"),
        Category("Massage", "massage"),
        Category("Public", "public"),
        Category("Outdoor", "outdoor"),
        Category("Homemade", "homemade"),
        Category("POV", "pov"),
        Category("Casting", "casting"),
        Category("Bondage", "bondage"),
        Category("Big Tits", "big-tits"),
        Category("Big Ass", "big-ass"),
        Category("Handjob", "handjob"),
        Category("Facial", "facial"),
        Category("Solo", "solo"),
        Category("Shemale", "shemale"),
        Category("Ladyboy", "ladyboy"),
        Category("Transsexual", "transsexual")
    )

    fun forOrientation(orientation: Prefs.Orientation): List<Category> = when (orientation) {
        Prefs.Orientation.STRAIGHT -> straight
        Prefs.Orientation.GAY -> gay
        Prefs.Orientation.TRANS -> trans
    }

    fun shuffled(orientation: Prefs.Orientation): List<Category> =
        forOrientation(orientation).shuffled()
}
