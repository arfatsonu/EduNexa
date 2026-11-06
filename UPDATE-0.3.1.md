# EduNexa 0.3.1

- Admin dashboard shows school counts first. Replaced three tall priority cards with two compact attendance/fee buttons below the counts. School Setup remains in More.
- Teacher dashboard retains clickable metrics, Diary/Marks actions and assigned-class attendance; removed repeated priority card. Header identifies the role, with the name retained in Profile and drawer.
- Student dashboard retains welcome, clickable metrics and actual diary/test records. Timeline is in Quick Access and More; removed repeated priority cards.

Verification: clean Android APK build passed; all 251 unit tests passed (0 failures/errors/skipped); school-isolation check passed; Teacher/Student routes and role data access reviewed in source. Live sign-in, phone layout, camera and real-school writes need device verification. No Firebase rules or live school data changed.

## Device review

1. Admin: counts appear immediately below the title/session; two compact actions follow the counts. More → School Setup still opens the checklist. Fees → Scan Student Card opens the selected pupil’s fees without recording a payment.
2. Teacher: test both a class-incharge and a subject-only account. Assigned classes, Diary, Tests/Marks and Datesheets must open only assigned data. Attendance remains limited to the incharge class.
3. Student: name appears in the welcome card; Attendance, Diary, Fees, published Results, Report and Timeline open the signed-in pupil’s own records. No raw session UUID is displayed.
4. Check a small phone and large system text for clipped school names, labels and bottom navigation.

Device review has not been performed in this environment.
