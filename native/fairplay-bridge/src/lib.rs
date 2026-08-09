use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::jbyteArray;
use shairplay::crypto::fairplay::FairPlay;

fn fail(env: &mut JNIEnv<'_>, message: impl AsRef<str>) -> jbyteArray {
    let _ = env.throw_new("java/lang/IllegalStateException", message.as_ref());
    std::ptr::null_mut()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_atrishub_atriscast_airplay_FairPlayNative_nativeDecryptSessionKey(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    key_message: JByteArray<'_>,
    encrypted_key: JByteArray<'_>,
) -> jbyteArray {
    let key_message = match env.convert_byte_array(&key_message) {
        Ok(value) => value,
        Err(error) => return fail(&mut env, format!("Could not read FairPlay key message: {error}")),
    };
    let encrypted_key = match env.convert_byte_array(&encrypted_key) {
        Ok(value) => value,
        Err(error) => return fail(&mut env, format!("Could not read FairPlay encrypted key: {error}")),
    };

    let key_message: [u8; 164] = match key_message.try_into() {
        Ok(value) => value,
        Err(_) => return fail(&mut env, "FairPlay key message must be 164 bytes"),
    };
    let encrypted_key: [u8; 72] = match encrypted_key.try_into() {
        Ok(value) => value,
        Err(_) => return fail(&mut env, "FairPlay encrypted key must be 72 bytes"),
    };

    let mut fairplay = FairPlay::new();
    if let Err(error) = fairplay.handshake(&key_message) {
        return fail(&mut env, format!("FairPlay phase-2 key message rejected: {error}"));
    }
    let decrypted = match fairplay.decrypt(&encrypted_key) {
        Ok(value) => value,
        Err(error) => return fail(&mut env, format!("FairPlay key decryption failed: {error}")),
    };

    match env.byte_array_from_slice(&decrypted) {
        Ok(result) => result.into_raw(),
        Err(error) => fail(&mut env, format!("Could not return FairPlay key to Android: {error}")),
    }
}
