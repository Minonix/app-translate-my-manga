# kotlinx.serialization (https://github.com/Kotlin/kotlinx.serialization#android)
# Les regles generales (garder les $$serializer, Companion.serializer()) sont deja
# fournies par consumer-rules.pro de la bibliotheque. Seule InnerClasses est a
# declarer cote app pour ces regles consommees. Aucune classe de ce module n'a de
# companion object nomme (verifie par recherche exhaustive), donc les regles -if/
# -keepnames specifiques aux companions nommes de la doc ne s'appliquent pas ici.
-keepattributes InnerClasses

# Modeles protobuf (sauvegarde Mihon .tachibk, lus via kotlinx.serialization.protobuf) :
# memes classes @Serializable que ci-dessus, couvertes par les memes regles.
-keep class com.tmm.mobile.mihon.** { *; }
