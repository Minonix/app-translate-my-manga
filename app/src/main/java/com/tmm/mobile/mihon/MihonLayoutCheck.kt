package com.tmm.mobile.mihon

// CAHIER_DES_CHARGES.md 8.1 : le dossier choisi a l'onboarding doit contenir downloads/, local/ et
// autobackup/. Logique pure/testable uniquement ici ; le branchement ViewModel/DI reel de
// l'onboarding etape 2 (necessite un AppContainer instancie avec un MihonTree lie a l'URI SAF
// choisie) est reporte a E10, comme l'heuristique "chapitre FR local sans jobId" de E5.
data class MihonFolderCheck(val name: String, val found: Boolean)

object MihonLayoutCheck {
    val REQUIRED_FOLDERS = listOf("downloads", "local", "autobackup")

    fun check(fs: MihonFs, rootDocId: String): List<MihonFolderCheck> {
        val children = fs.children(rootDocId)
        return REQUIRED_FOLDERS.map { name ->
            MihonFolderCheck(name, children.any { it.isDirectory && it.name == name })
        }
    }

    fun canContinue(checks: List<MihonFolderCheck>): Boolean = checks.all { it.found }
}
