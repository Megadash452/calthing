use std::path::PathBuf;
use ez_jni::{call, jni_fn, new, FromException, FromObject, ToObject};
use jni::{JNIEnv, objects::JObject};
use classes::fs::file_stem;
use crate::{get_app_dir, DocUri, ExternalDir, ILLEGAL_FILE_CHARACTERS, SUFFIX_DIR};

#[derive(Debug, FromObject, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
#[class(me.marti.calprovexample.Color)]
struct Color {
    pub r: u8,
    pub g: u8,
    pub b: u8
}
impl<'local> ToObject<'local> for Color {
    fn to_object(&self, env: &mut JNIEnv<'local>) -> JObject<'local> {
        new!(me.marti.calprovexample.Color(u8(self.r), u8(self.g), u8(self.b)))
    }
}

jni_fn! { me.marti.calprovexample.jni.DavSyncRs =>
    /// Create the files in internal and external storage for a new Calendar the user created.
    /// 
    /// If **external_dir_uri** is **`NULL`**, only the file in app storage will be created.
    /// **file_name** is the name of the file that will be created in each directory (e.g. `"name.ics"`). */
    pub fn create_calendar_files<'local>(
        context: android.content.Context,
        file_name: String,
        color: me.marti.calprovexample.Color,
        external_dir_uri: Option<android.net.Uri>,
    ) {
        let davsyncrs = env.get_static_field("me/marti/calprovexample/jni/DavSyncRs", "INSTANCE", "Lme/marti/calprovexample/jni/DavSyncRs;")
            .unwrap().l().unwrap();
        call!(davsyncrs.initialize_dirs(
            android.content.Context(context),
            android.net.Uri(external_dir_uri.to_object(env))
        ) -> void);
        let external_dir_uri = external_dir_uri.map(|uri| DocUri::from_tree_uri(env, uri).unwrap());
        
        // Check for illegal characters
        if file_name.contains(ILLEGAL_FILE_CHARACTERS) {
            panic!("File name can't contain the following characters: {ILLEGAL_FILE_CHARACTERS:?}")
        }

        // Create file in App's internal storage
        std::fs::File::create_new(get_app_dir(env, &context).join(SUFFIX_DIR).join(&file_name))
            .unwrap_or_else(|err| panic!("Error creating file in internal directory: {err}"));
        // Create file in external directory in shared storage
        if let Some(external_dir_uri) = external_dir_uri {
            ExternalDir::new(context, external_dir_uri, env)
                .unwrap_or_else(|| panic!("external_dir_uri does not point to a directory"))
                .create_file_at(env, PathBuf::from(SUFFIX_DIR).join(&file_name))
                .unwrap_or_else(|err| panic!("Error creating external file: {err}"));
        }

        write_color_to_calendar_file(
            file_stem(&file_name),
            Color::from_object(&color, env)
                .unwrap_or_else(|err| panic!("Error getting color: {err}"))
        );
    }

    /// Read a *Calendar file* and write the data to the Calendar *Content Provider*.
    /// 
    /// Creates a new Calendar in the Content Provider if one with **name** does not exist.
    pub fn write_file_data_to_calendar<'local>(
        perm: me.marti.calprovexample.ui.CalendarPermission,
        name: String,
        color: Option<me.marti.calprovexample.Color>
    ) {
        #[derive(FromException)]
        #[class(me.marti.calprovexample.ElementExistsException)]
        struct ElementExists;
        
        // let context = call!(perm.getContext() -> android.content.Context);

        // Create the calendar if it does not exist. Ignore the result
        let _ = call!(static me.marti.calprovexample.calendar.ActionsKt.newCalendar(
            me.marti.calprovexample.ui.CalendarPermissionScope(perm),
            String(name),
            me.marti.calprovexample.Color(color.to_object(env)),
            kotlin.coroutines.Continuation(JObject::null())
        ) -> Result<Option<java.lang.Object>, ElementExists>)
            .inspect(|r| if r.is_none() { panic!("Failed creating calendar") });
        
        // TODO: parse file contents and add them to the Content Provider
        // TODO: add to list without adding to provider
    }

    pub fn write_calendar_data_to_file<'local>(name: String) {
        // TODO:
    }

    pub fn write_color_to_calendar_file<'local>(name: String, color: me.marti.calprovexample.Color) {
        // TODO:
    }
}

fn write_color_to_calendar_file(name: &str, color: Color) {
    // TODO:
}
