# TournamentKit — אפיון מלא ל-SDK טורנירים וליגות

**סמינר סלולר — פרויקט SDK | מגיש: ליעד נווה**

---

## 1. הרעיון והצורך שהוא פותר

### הרעיון
TournamentKit הוא SDK לאנדרואיד שמעניק לכל אפליקציה מערכת טורנירים וליגות מלאה — הרשמה, חלוקה לבתים, ברקטים (נוק-אאוט), טבלאות ליגה, דיווח תוצאות, דירוגים ועדכונים בזמן אמת — בלי שהמפתח יצטרך לבנות שום דבר מזה בעצמו.

### הצורך
כמעט כל אפליקציה עם מרכיב תחרותי נתקלת באותה בעיה: לבנות מערכת טורנירים זה פרויקט שלם בפני עצמו. צריך אלגוריתם שיבוץ, ניהול מצב (מי נגד מי, מי עבר שלב), חישובי ניקוד, סנכרון בין משתתפים, וממשק ניהול. מפתחים של משחקי מובייל, אפליקציות ספורט חובבני (כדורגל שכונתי, פאדל, שחמט), חדרי כושר, ואפילו אפליקציות טריוויה — כולם ממציאים את אותו גלגל.

TournamentKit הופך את זה ל-3 שורות קוד: `init`, `joinTournament`, `reportResult` — וכל השאר קורה בשרת.

### למה זה SDK ולא "עוד אפליקציה"
- **הלקוח הוא מפתח**, לא משתמש קצה. הערך נמדד בכמה קל למפתח להטמיע.
- **הלוגיקה חיה בשרת**: שיבוץ בתים, התקדמות ברקט, חישוב טבלת ליגה — אי אפשר לממש לוקאלית כי טורניר הוא בהגדרה רב-משתמשים ורב-מכשירים.
- **הפורטל משנה התנהגות**: שינוי שיטת ניקוד או חוקי טורניר בפורטל משנה מיידית את מה שכל האפליקציות המחוברות מציגות ומחשבות — בלי שחרור גרסה.
- **כל הדאטה נוצר אצלנו** (משתמשים + פורטל). אין תלות ב-API חיצוני.

---

## 2. רשימת פיצ'רים ויכולות

### יכולות ליבה (MVP)
1. **יצירת טורניר** משלושה סוגים: נוק-אאוט (Single Elimination), ליגה (Round Robin), ושלב בתים + נוק-אאוט.
2. **הרשמה והצטרפות** — לפי קוד הצטרפות או רשימה פתוחה, עם תקרת משתתפים.
3. **שיבוץ אוטומטי** — הגרלת בתים/ברקט בשרת, כולל Bye אוטומטי כשמספר המשתתפים אינו חזקה של 2.
4. **דיווח תוצאות** — משתתף מדווח תוצאה; אופציונלית נדרש אישור מהיריב או ממנהל (מוגדר בפורטל).
5. **התקדמות אוטומטית** — השרת מחשב מנצחים, מקדם בברקט, מעדכן טבלת ליגה (נקודות, הפרש שערים/סטים).
6. **לוח תוצאות ודירוג** — לכל טורניר, וכן דירוג מצטבר (ELO פשוט) לאורך טורנירים.
7. **עדכונים בזמן אמת** — callback באפליקציה כשמשחק אחר מסתיים או כשהברקט מתעדכן.
8. **רכיבי UI מוכנים** — BracketView (ברקט גרפי), LeagueTableView (טבלת ליגה), MatchCard.
9. **Offline ועמידות** — דיווחי תוצאה נשמרים ב-cache מקומי ונשלחים אוטומטית בחזרת רשת; retry עם backoff.
10. **Multi-project** — כל מפתח (API Key) רואה רק את הטורנירים שלו; בידוד מלא בין פרויקטים.

### יכולות פורטל
- בניית **תבניות טורניר** (Templates) עם חוקים: שיטת ניקוד, גודל, האם נדרש אישור תוצאות, משך זמן לדיווח.
- ניהול חי: הקפאה/ביטול טורניר, פסילת משתתף, תיקון תוצאה ידני.
- אנליטיקות: משתתפים פעילים, אחוז טורנירים שהסתיימו, זמן ממוצע לטורניר, גרף הצטרפות.
- ניהול API Keys ופרויקטים.

---

## 3. פונקציות חשופות למפתח (Public API)

```kotlin
// 1. אתחול — חובה לפני כל שימוש
TournamentKit.init(context: Context, apiKey: String, config: TKConfig? = null)

// 2. זיהוי המשתמש באפליקציה המארחת
TournamentKit.identify(userId: String, displayName: String, avatarUrl: String?)

// 3. יצירת טורניר מתבנית שהוגדרה בפורטל
TournamentKit.createTournament(
    templateId: String,
    name: String,
    callback: TKCallback<Tournament>
)

// 4. הצטרפות לטורניר
TournamentKit.joinTournament(joinCode: String, callback: TKCallback<Participant>)

// 5. דיווח תוצאה
TournamentKit.reportResult(
    matchId: String,
    score: TKScore,            // למשל TKScore(home = 3, away = 1)
    callback: TKCallback<Match>
)

// 6. שליפת מצב נוכחי
TournamentKit.getTournament(tournamentId: String, callback: TKCallback<Tournament>)
TournamentKit.getBracket(tournamentId: String, callback: TKCallback<Bracket>)
TournamentKit.getLeaderboard(tournamentId: String, callback: TKCallback<List<Standing>>)

// 7. האזנה לעדכונים בזמן אמת
TournamentKit.onTournamentUpdate(tournamentId: String, listener: TKUpdateListener)
TournamentKit.removeUpdateListener(tournamentId: String)
```

### Callbacks וטיפול בשגיאות
```kotlin
interface TKCallback<T> {
    fun onSuccess(result: T)
    fun onError(error: TKError)   // קוד שגיאה + הודעה קריאה
}

// קודי שגיאה עיקריים:
// TK_NOT_INITIALIZED, TK_INVALID_API_KEY, TK_NETWORK_ERROR (נכנס לתור offline),
// TK_TOURNAMENT_FULL, TK_MATCH_ALREADY_REPORTED, TK_NOT_PARTICIPANT,
// TK_TOURNAMENT_LOCKED (הוקפא בפורטל)
```

### דוגמת שימוש מלאה (מה שייכנס ל-README)
```kotlin
TournamentKit.init(this, "tk_live_3f9a...")
TournamentKit.identify("user_42", "Liad", null)

TournamentKit.joinTournament("FIFA24") { result ->
    result.onSuccess { participant ->
        TournamentKit.onTournamentUpdate(participant.tournamentId) { update ->
            bracketView.render(update.bracket)   // רכיב UI מובנה
        }
    }
}
```

---

## 4. פונקציות פנימיות בספרייה (Internal)

| פונקציה פנימית | תפקיד |
|---|---|
| `AuthManager.attachHeaders()` | מצרף API Key + Project ID לכל בקשה |
| `RequestQueue.enqueue(request)` | תור בקשות; דיווחי תוצאה נכנסים לתור גם ב-offline |
| `OfflineStore` (Room) | שמירה מקומית של דיווחים שלא נשלחו + cache של מצב טורניר אחרון |
| `RetryPolicy.execute()` | Exponential backoff: ניסיון חוזר אחרי 2s, 4s, 8s (עד 5 ניסיונות) |
| `SyncEngine.flush()` | בחזרת רשת (ConnectivityManager listener) — שולח את כל התור לפי סדר |
| `ListenerManager` | עדכוני זמן אמת: Firestore snapshot listeners על מסמכי הטורניר (read-only), עם ניהול attach/detach |
| `CacheManager.get/put(key, ttl)` | Cache בזיכרון + דיסק למצב ברקט/טבלה; Firestore offline persistence משלים את זה ברמת הקריאות |
| `Validator.validateScore()` | ולידציה לוקאלית לפני שליחה (תוצאה שלילית, משחק לא של המשתמש וכו') |
| `TKLogger` | לוגים פנימיים + דיווח שגיאות SDK לשרת (לטובת המפתח בפורטל) |
| `LifecycleObserver` | ניתוק snapshot listeners כשהאפליקציה ברקע — חיסכון בסוללה, רשת וקריאות Firestore |

---

## 5. פונקציות בשרת (Ktor — REST Endpoints)

```
POST  /createTournament      יצירת טורניר מתבנית
POST  /joinTournament        הצטרפות משתתף (לפי joinCode)
POST  /startTournament       נעילת הרשמה + הגרלת שיבוץ (בניית כל עץ המשחקים)
POST  /reportResult          דיווח תוצאה
POST  /confirmResult         אישור תוצאה ע"י יריב (אם נדרש בתבנית)
GET   /getTournament         מצב טורניר מלא (לשליפה חד-פעמית / fallback)
GET   /getStandings          טבלת ליגה / דירוג
GET   /getUserRating         דירוג ELO מצטבר

זמן אמת: אין צורך ב-endpoint — ה-SDK פותח Firestore snapshot listener
(read-only) על מסמך הטורניר ועל collection המשחקים שלו.
```

### לוגיקה שרצה אך ורק בשרת
1. **אלגוריתם שיבוץ**: הגרלה אקראית / לפי דירוג (seeding), חלוקה לבתים מאוזנים, השלמת Bye.
2. **מנוע התקדמות**: בקבלת תוצאה מאושרת — קביעת מנצח לפי חוקי התבנית, קידום לברקט הבא, או עדכון טבלת ליגה (3-1-0 או כפי שהוגדר), כולל שוברי שוויון (הפרש, מפגש ישיר).
3. **אימות והרשאות**: כל בקשה מאומתת לפי API Key → Project; רק משתתף במשחק יכול לדווח עליו.
4. **חישוב ELO** מצטבר אחרי כל משחק.
5. **כתיבה ל-Firestore בטרנזקציה** — עדכון Match + Standing + המשחק הבא בברקט קורה אטומית; ה-snapshot listeners של כל המאזינים מתעדכנים מיידית כתוצאה מהכתיבה (אין צורך במנגנון push נפרד).

### פונקציות פורטל (צד שרת ייעודי לפורטל)
```
CRUD   /portal/templates                ניהול תבניות טורניר
GET    /portal/tournaments              כל הטורנירים בפרויקט + סינון
POST   /portal/tournaments/{id}/freeze  הקפאה / ביטול
PATCH  /portal/matches/{id}/override    תיקון תוצאה ידני (Audit log)
GET    /portal/analytics                אגרגציות: משתתפים, השלמות, פעילות יומית
CRUD   /portal/keys                     ניהול API Keys
```

---

## 6. מבנה האובייקטים בשרת והארכיטקטורה

### סטאק — Kotlin מקצה לקצה
- **Client SDK**: Kotlin (Android), מופץ כספריית AAR / מודול Gradle. Room ל-offline queue, Retrofit/OkHttp לקריאות לשרת, Firebase SDK להאזנות זמן אמת.
- **Server API**: **Ktor (Kotlin) על Cloud Run** — כאן ורק כאן רצה הלוגיקה: שיבוץ, מנוע התקדמות, אימות API Key, חישובי טבלה. השרת מדבר עם Firestore דרך **Firebase Admin SDK ל-Kotlin/Java**.
- **מודול משותף**: data classes של Tournament / Match / Standing חיים במודול Kotlin משותף לשרת ול-SDK — מקור אמת אחד למודלים, בלי כפילויות.
- **DB**: **Firestore** — מבני טורניר הם היררכיים ומשתנים לפי סוג (ברקט ≠ ליגה), מה שמתאים למסמכים גמישים. כתיבה ישירה מהלקוח **חסומה** ב-Security Rules.
- **Portal**: אפליקציית Web (React) על Firebase Hosting, מאחורי Firebase Auth.

> **עיקרון מפתח**: ה-SDK לעולם לא כותב ישירות ל-Firestore. כל פעולה (join, reportResult, create) עוברת דרך שרת ה-Ktor שמאמת ומריץ לוגיקה בצד השרת. Firestore משמש את הלקוח **לקריאה והאזנה בלבד** (read-only ב-Security Rules). זה מה שהופך את Firebase לשרת אמיתי ולא ל"מסד נתונים חשוף" — ועונה ישירות על סימן האזהרה "אין שימוש אמיתי בשרת".

### מודל הנתונים (Firestore Collections)

```
projects/{projectId}
  { name, apiKeyHash, createdAt }

projects/{projectId}/templates/{templateId}
  { type: "knockout"|"league"|"groups+knockout",
    scoring: {win, draw, loss}, maxParticipants,
    requireConfirmation: bool, reportTimeoutHours }

tournaments/{tournamentId}
  { projectId, templateId, name, joinCode,
    status: "registration"|"active"|"finished"|"frozen",
    participants: [{userId, displayName, seed}],
    rules: {...},                 // Snapshot של חוקי התבנית בזמן ההתחלה
    createdAt, startedAt }

tournaments/{tournamentId}/matches/{matchId}
  { round, slot,                  // מיקום בברקט: סיבוב + אינדקס
    homeId, awayId,
    score: {home, away} | null,
    status: "pending"|"reported"|"confirmed",
    nextMatchId | null }          // לאן המנצח מתקדם

tournaments/{tournamentId}/standings/{userId}
  { played, won, drawn, lost,
    pointsFor, pointsAgainst, points }   // מסמך מחושב

ratings/{projectId}_{userId}
  { elo, gamesPlayed }
```

**למה matches ו-standings הם sub-collections של tournament?** כי ה-SDK פותח listener אחד על `tournaments/{id}/matches` ומקבל את כל הברקט + כל עדכון עתידי בערוץ אחד. ב-Firestore, listener על collection מחזיר רק deltas אחרי ה-snapshot הראשון — יעיל מאוד ברוחב פס.

### החלטות ארכיטקטורה — ולמה (הסעיף שהמרצה ביקש להעמיק בו)

**1. שליפת ברקט בקריאה אחת — denormalization מכוון.**
האפליקציה צריכה לצייר ברקט שלם במסך אחד. במקום N שאילתות (משחק-משחק), כל ה-Matches של טורניר נשלפים בשאילתה אחת לפי `tournamentId` (אינדקס) וממוינים לפי `round, slot`. שמות המשתתפים מוטמעים בתוך מסמך ה-Tournament (denormalized) כדי לא לעשות join מול טבלת users בכל רינדור. **המחיר**: עדכון שם משתמש דורש עדכון בכמה מקומות — מחיר זניח כי שמות כמעט לא משתנים, לעומת שליפת ברקט שקורית עשרות פעמים בדקה.

**2. Standing כמסמך מחושב מראש (Write-time aggregation), לא חישוב בכל קריאה.**
טבלת ליגה נקראת הרבה יותר משהיא נכתבת (כל פתיחת מסך מול דיווח תוצאה אחד). לכן בכל אישור תוצאה שרת ה-Ktor מעדכן את שני מסמכי ה-standing הרלוונטיים בעדכון אטומי (`FieldValue.increment`), ושליפת הטבלה היא query ממוין פשוט. **האלטרנטיבה** (סריקה של כל ה-matches בכל קריאה) הייתה יקרה ליניארית במספר המשחקים — וב-Firestore גם עולה כסף פר-קריאת-מסמך, אז המודל הזה חוסך ממש בעלויות.

**3. `nextMatchId` שמור מראש בכל Match.**
כשהשרת מגריל ברקט, הוא בונה את כל עץ המשחקים מראש כולל מצביעים קדימה. קידום מנצח = עדכון שדה אחד במשחק הבא. אין צורך לחשב "לאן מתקדמים" בזמן ריצה — זה הופך את מנוע ההתקדמות לטריוויאלי, וכולו עטוף ב-**Firestore Transaction** אחת (Match + Standing + המשחק הבא) כך שאין מצב ביניים לא עקבי.

**4. Snapshot של חוקי התבנית בתוך הטורניר.**
אם המפתח משנה תבנית בפורטל באמצע טורניר פעיל — הטורניר ממשיך לפי החוקים שאיתם התחיל (הוגנות), וטורנירים חדשים מקבלים את החוקים החדשים. זה גם ההסבר למבחן "הפורטל משנה התנהגות": שינוי תבנית משפיע מיידית על כל טורניר חדש בכל האפליקציות.

**5. Security Rules — חלוקת אחריות בין קריאה לכתיבה.**
```
allow read:  משתתף בטורניר בלבד (לפי uid ברשימת participants)
allow write: false   // לעולם — כתיבה רק דרך שרת ה-Ktor (Admin SDK)
```
זה לב הארכיטקטורה: הלקוח מקבל זמן-אמת בחינם מ-Firestore, אבל אינו יכול לזייף תוצאה, לשנות ברקט או לגשת לפרויקט אחר. כל ולידציה והרשאה נאכפת בשרת.

**6. זמן אמת: Firestore snapshot listeners במקום WebSocket.**
ה-SDK פותח listener על `tournaments/{id}/matches`. Firestore מנהל בעצמו reconnect, offline cache ושליחת deltas בלבד. זה חוסך לנו לכתוב שכבת WebSocket + polling שלמה, ומקטין דרמטית את שטח הבאגים — ובדמו זה נראה מצוין: שני מכשירים, דיווח באחד, הברקט קופץ בשני תוך שנייה.

### תרשים ארכיטקטורה (סכמטי)

```
┌─────────────┐     ┌──────────────┐  HTTPS  ┌──────────────────┐
│ Developer    │     │ Client SDK   │ ──────► │ Ktor Server       │
│ App (Demo)   │ ──► │ TournamentKit│ (כתיבה) │ (Kotlin·Cloud Run)│
│              │     │ cache+offline│         └────────┬─────────┘
└─────────────┘     └──────┬───────┘                  │ Admin SDK
                           │ snapshot listeners        ▼
                           │ (קריאה בלבד)      ┌──────────────┐
                           └─────────────────► │  Firestore    │
                                               │ Security Rules│
                                               └──────▲───────┘
                                          ┌───────────┴────────┐
                                          │ Portal (React Web) │
                                          │ Firebase Hosting   │
                                          │ + Firebase Auth    │
                                          └────────────────────┘
```

---

## 7. סקיצות (לשרטוט ידני לפי המתווה הזה)

### פורטל — מסך ראשי (Dashboard)
```
┌──────────────────────────────────────────────────────┐
│ TournamentKit Portal      [Project: MyGame ▼] [Keys] │
├──────────────┬───────────────────────────────────────┤
│ ▸ Dashboard  │  טורנירים פעילים: 12   משתתפים: 340   │
│ ▸ Tournaments│  ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│ ▸ Templates  │  │ גרף      │ │ אחוז     │ │ זמן      │  │
│ ▸ Analytics  │  │ הצטרפות │ │ השלמה   │ │ ממוצע    │  │
│ ▸ API Keys   │  └─────────┘ └─────────┘ └─────────┘  │
│              │  טבלת טורנירים אחרונים [הקפא][צפה]    │
└──────────────┴───────────────────────────────────────┘
```

### פורטל — עורך תבנית
```
┌──────────────────────────────────────────┐
│ תבנית חדשה                                │
│ שם: [________]   סוג: (•) נוק-אאוט        │
│                  ( ) ליגה  ( ) בתים+נוקאאוט│
│ ניקוד: ניצחון [3] תיקו [1] הפסד [0]       │
│ מקס' משתתפים: [16]                        │
│ [✓] דרוש אישור תוצאה מהיריב               │
│ זמן לדיווח: [48] שעות                     │
│            [שמור תבנית]                   │
└──────────────────────────────────────────┘
```

### רכיב UI בספרייה — BracketView (סכמטי)
```
רבע גמר        חצי גמר       גמר
┌──────┐
│ דני 3 │──┐
│ רון 1 │  ├──┌──────┐
└──────┘  │  │ דני   │──┐
┌──────┐  │  │ גיא 2 │  │
│ גיא   │──┘  └──────┘  ├── ┌─────┐
└──────┘                │   │  ?  │
   ...                  ──┘ └─────┘
```
+ MatchCard (כרטיס משחק עם כפתור "דווח תוצאה") + LeagueTableView (טבלה ממוינת).

---

## 8. אפליקציות וסוגי אפליקציות שיכולות להשתמש ב-SDK

| קטגוריה | דוגמאות |
|---|---|
| משחקי מובייל | טורניר שחמט/דמקה/טריוויה בתוך המשחק, ליגות שבועיות |
| ספורט חובבני | אפליקציית כדורגל שכונתי, ליגת פאדל/טניס, טורניר FIFA בין חברים |
| חדרי כושר וסטודיו | אתגרי "מי הרים הכי הרבה", טורניר קרוספיט פנימי |
| ארגונים וקהילות | ליגת פינג-פונג משרדית, טורניר בין מחלקות, תנועות נוער |
| חינוך | תחרויות חידונים בין כיתות, ליגת מתמטיקה |
| eSports קטן | טורנירי קהילה למשחקים שאין להם מערכת מובנית |

המכנה המשותף: כל אפליקציה עם תחרות בין משתמשים — בדיוק הדרישה במייל: "שיהיה גנרי ומתאים להרבה אפליקציות".

---

## 9. מחקר פלטפורמה: מה קורה לפני ואחרי init (אנדרואיד)

לפי דרישת המרצה — מה ניתן להוציא מהפלטפורמה ואיך מתמודדים עם מצבי קצה:

1. **לפני `init` אין כלום**: כל קריאה ל-API לפני אתחול זורקת `TK_NOT_INITIALIZED` עם הודעה ברורה — לא קריסה. זה סטנדרט בכל SDK (כך נוהג גם Firebase).
2. **נתונים שנאספים ב-init (בלי הרשאות מיוחדות)**: גרסת אנדרואיד (`Build.VERSION`), דגם מכשיר (`Build.MODEL`), שפה ואזור (`Locale`), וגרסת האפליקציה המארחת (`PackageManager`). הנתונים משמשים את הפורטל לאנליטיקת מכשירים ברמה מצרפית (התפלגות גרסאות ודגמים). לא נאסף מזהה מכשיר או מזהה התקנה — הזיהוי בבקשות הוא ברמת המשתמש (`userId` מ-identify) והפרויקט בלבד.
3. **דיווח לפני סגירת המכשיר/אפליקציה**: דיווח תוצאה נכתב קודם ל-Room (דיסק) ורק אז נשלח. אם האפליקציה נהרגת לפני שהשליחה הסתיימה — ה-`SyncEngine` שולח ב-init הבא. לאירועים קריטיים נשתמש ב-WorkManager עם constraint של רשת — אנדרואיד מריץ את העבודה גם אם האפליקציה לא פתוחה.
4. **חיי רקע**: ב-`onStop` של האפליקציה — ניתוק snapshot listeners (חיסכון בסוללה ובקריאות Firestore; Doze mode ממילא מגביל רשת ברקע). חזרה ל-foreground → ה-listener נפתח מחדש ו-Firestore שולח אוטומטית רק את ה-deltas שפוספסו.
5. **בדיקת רשת**: `ConnectivityManager.NetworkCallback` — ברגע שחוזרת רשת, flush אוטומטי של תור הדיווחים. בנוסף, Firestore offline persistence נותן לקריאות לעבוד מה-cache גם בלי רשת.
6. **אבטחת API Key בצד לקוח**: ה-Key מזהה את הפרויקט אך אינו סוד מוחלט (אי אפשר להסתיר באמת ב-APK). לכן ההרשאות מינימליות: ה-Key מאומת בשרת ה-Ktor בכל בקשה ומאפשר רק דיווח/קריאה; Firestore Security Rules חוסמות כל כתיבה ישירה; פעולות ניהול דורשות Firebase Auth של הפורטל. בנוסף — rate limiting per key בתוך השרת.
7. **הערה תפעולית**: Cloud Run (וכל שירותי Google Cloud בחיוב) דורש הפעלת billing בפרויקט — יש שכבת חינם נדיבה שמספיקה לפרויקט סמינר, אבל צריך כרטיס אשראי בהגדרה. שווה לדעת מראש.

---

## 10. שדרוגים טבעיים בהמשך (להראות שיש לאן לצמוח)

- צ'אט פר-משחק / פר-טורניר
- פרסים ותגים אוטומטיים (חיבור טבעי לגיימיפיקציה)
- שיבוץ לפי דירוג ELO (seeding חכם)
- טורנירים מתוזמנים (סיבוב נפתח כל יום)
- Push notifications ("המשחק שלך מוכן!")
- SDK ל-iOS / Web

---

## 11. נספח: עמידה בכל מבחני המרצה

| מבחן | איך TournamentKit עומד בו |
|---|---|
| מפתח באמת ירצה לחבר את זה? | כן — בניית מערכת טורנירים עצמאית = שבועות עבודה |
| ערך מעבר לפונקציה מקומית? | כן — רב-משתמשים, רב-מכשירים, חייב שרת |
| השרת והפורטל מוסיפים ערך אמיתי? | כל הלוגיקה בשרת Ktor (Kotlin); כתיבה ישירה ל-DB חסומה; הפורטל קובע חוקים ומשנה התנהגות |
| "אין שימוש אמיתי בשרת"? | נשלל במפורש: הלקוח read-only מול Firestore, כל פעולה עוברת ולידציה ולוגיקה ב-Function |
| הדגמה תוך 2 דקות? | פורטל: יצירת תבנית ← אפליקציה: join בקוד ← דיווח תוצאה ← הברקט מתעדכן חי |
| מקום לשדרוגים? | סעיף 10 |
| לא wrapper לפונקציה אחת? | 9 פונקציות ציבוריות + מנוע שרת שלם |
| הפורטל לא רק raw data? | תבניות, הקפאה, override, אנליטיקות מחושבות |
| הפרדה בין פרויקטים? | API Key → Project, בידוד מלא בכל שאילתה |
| לא מבוסס API חיצוני (המייל) | 100% מהדאטה נוצר ע"י המשתמשים והפורטל |
| גנרי להרבה אפליקציות (המייל) | סעיף 8 — שש קטגוריות שונות |
