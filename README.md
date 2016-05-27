# light-magique

Quelques notes concernant le protocole, afin que je n'oublie pas comment ça fonctionne. La documentation officielle paraîtra prochainement.

* Les envois de données se mettent sous la forme d'unités de sens appelées messages.
* Un message comporte un header et un corps.
* Le header est un byte, et indique le type du corps du message.
* Chaque type de corps a une taille fixée, qui est un nombre entier de bytes.

* Byte 0 : Player update. Corps : Entity update + Shooter update
* Byte 1 : Enemy update. Corps : Entity update + Shooter update
* Byte 2 : Entity update. Corps : Entity update
* Byte 3 : Changement d'entity id. Corps : Ancien puis nouvel id
* Byte 4 : Notification d'ennemi tué. Corps : Aucun
* Byte 5 : Notification de vague. Corps : Numéro de la vague
* Byte 6 : Notification de fin de vague. Corps : Aucun
* Byte 7 : Notification de terminaison du jeu. Corps : Aucun

On commence le jeu en envoyant à chaque joueur un message sans header composé d'uniquement de l'id du joueur.


En cas d'erreur, le client copie la stack trace dans le presse-papiers.