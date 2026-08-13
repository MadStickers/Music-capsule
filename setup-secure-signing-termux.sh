#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

backup_dir="$HOME/MusicCapsule-signing-backup"
key_file="$backup_dir/music-capsule-release.jks"
credentials_file="$backup_dir/credentials.txt"
key_alias="music-capsule-release"

if [ -e "$key_file" ] || [ -e "$credentials_file" ]; then
  echo "STOP: signing backup already exists at $backup_dir"
  echo "Do not regenerate it. Use the existing key for every future update."
  exit 1
fi

for command_name in keytool openssl gh base64 git; do
  command -v "$command_name" >/dev/null || {
    echo "Missing command: $command_name"
    echo "Run: pkg install openjdk-17 openssl-tool gh git coreutils -y"
    exit 1
  }
done

git rev-parse --is-inside-work-tree >/dev/null
gh auth status >/dev/null

mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
store_password="$(openssl rand -hex 24 | cut -c 1-32)"
key_password="$(openssl rand -hex 24 | cut -c 1-32)"

keytool -genkeypair \
  -keystore "$key_file" \
  -storepass "$store_password" \
  -keypass "$key_password" \
  -alias "$key_alias" \
  -keyalg RSA -keysize 3072 -validity 10000 \
  -dname "CN=Music Capsule, OU=Personal Release, O=MadStickers, C=KZ" \
  -noprompt
chmod 600 "$key_file"

certificate_sha256="$(keytool -list -v -keystore "$key_file" -storepass "$store_password" -alias "$key_alias" | sed -n 's/.*SHA256: //p' | head -1 | tr -d ':' | tr '[:lower:]' '[:upper:]')"
test -n "$certificate_sha256"

{
  printf 'KEY_ALIAS=%s\n' "$key_alias"
  printf 'KEYSTORE_PASSWORD=%s\n' "$store_password"
  printf 'KEY_PASSWORD=%s\n' "$key_password"
  printf 'SIGNING_CERT_SHA256=%s\n' "$certificate_sha256"
} > "$credentials_file"
chmod 600 "$credentials_file"

base64 -w 0 "$key_file" | gh secret set SIGNING_KEY_BASE64
printf '%s' "$store_password" | gh secret set KEYSTORE_PASSWORD
printf '%s' "$key_password" | gh secret set KEY_PASSWORD
printf '%s' "$key_alias" | gh secret set KEY_ALIAS
printf '%s' "$certificate_sha256" | gh secret set SIGNING_CERT_SHA256

unset store_password key_password
echo "Secure signing configured for: $(gh repo view --json nameWithOwner -q .nameWithOwner)"
echo "Certificate SHA-256: $certificate_sha256"
echo "BACK UP THIS DIRECTORY OFF THE PHONE: $backup_dir"
echo "Never commit or share its contents. Losing it means future APK updates cannot be signed."
