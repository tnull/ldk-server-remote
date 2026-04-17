//! `ldk-server-remote-config`
//!
//! Emits a single-line `ldk-server-remote:` setup URI that matches the format consumed
//! by the Android app's QR scanner (see `util/ServerUri.kt`). Intended pipeline:
//!
//! ```text
//! ldk-server-remote-config \
//!     --data-dir ~/.ldk-server \
//!     --name "home signet" \
//!     --rpc-address 192.168.1.5:3000 \
//!     --network signet \
//!   | qrencode -o ~/setup.png
//! ```
//!
//! Data directory layout expected (matches LDK Server's on-disk format):
//!
//! ```text
//! <data-dir>/tls.crt           # PEM; written by the server on first start
//! <data-dir>/<network>/api_key # raw 32 random bytes; we hex-encode on emit
//! ```
//!
//! Where `<network>` is the LDK-Server directory name — `bitcoin` (not `mainnet`),
//! `testnet`, `testnet4`, `signet`, or `regtest`. We expose the user-facing name
//! (`mainnet`/…) to match the Android side and translate to the directory name
//! internally.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, Context, Result};
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use clap::{Parser, ValueEnum};

const URI_SCHEME: &str = "ldk-server-remote";
const URI_AUTHORITY: &str = "setup";
const URI_VERSION: &str = "1";

#[derive(Parser, Debug)]
#[command(about = "Emit an ldk-server-remote: setup URI for an LDK Server data directory.")]
struct Cli {
	/// Path to the LDK Server storage directory (the dir containing `tls.crt`
	/// plus `<network>/api_key`). Usually `~/.ldk-server` on Linux.
	#[arg(long)]
	data_dir: PathBuf,

	/// Free-form label shown in the client's server list (e.g. "home signet").
	#[arg(long)]
	name: String,

	/// Host:port the server is reachable at from the client's vantage point. Not
	/// inferable from the data directory — supply an address a remote phone can
	/// actually hit (LAN IP, public DNS name, etc.). Do not include a scheme.
	#[arg(long)]
	rpc_address: String,

	/// Bitcoin network the server runs on.
	#[arg(long, value_enum)]
	network: Network,
}

#[derive(Clone, Copy, Debug, ValueEnum, PartialEq, Eq)]
enum Network {
	Mainnet,
	Testnet,
	Signet,
	Regtest,
}

impl Network {
	/// Directory name LDK Server itself uses for this network. Notably `mainnet`
	/// maps to `bitcoin` on disk.
	fn data_subdir(self) -> &'static str {
		match self {
			Network::Mainnet => "bitcoin",
			Network::Testnet => "testnet",
			Network::Signet => "signet",
			Network::Regtest => "regtest",
		}
	}

	/// Network name as written into the URI — matches Android's
	/// `BitcoinNetwork.displayName()`.
	fn uri_name(self) -> &'static str {
		match self {
			Network::Mainnet => "mainnet",
			Network::Testnet => "testnet",
			Network::Signet => "signet",
			Network::Regtest => "regtest",
		}
	}
}

fn main() -> Result<()> {
	let cli = Cli::parse();
	let uri = build_uri(&cli)?;
	println!("{uri}");
	Ok(())
}

fn build_uri(cli: &Cli) -> Result<String> {
	if cli.name.trim().is_empty() {
		return Err(anyhow!("--name must not be empty"));
	}
	if cli.rpc_address.contains("://") {
		return Err(anyhow!(
			"--rpc-address should be host:port only, without a scheme; got {:?}",
			cli.rpc_address
		));
	}

	let api_key_hex = read_api_key(&cli.data_dir, cli.network)?;
	let cert_pem = read_certificate(&cli.data_dir)?;
	let cert_b64 = URL_SAFE_NO_PAD.encode(cert_pem.as_bytes());

	let query = format!(
		"v={}&name={}&network={}&url={}&apikey={}&cert={}",
		URI_VERSION,
		percent_encode(&cli.name),
		cli.network.uri_name(),
		percent_encode(&cli.rpc_address),
		percent_encode(&api_key_hex),
		cert_b64, // base64url is already URL-safe; no further percent-encoding needed.
	);
	Ok(format!("{URI_SCHEME}://{URI_AUTHORITY}?{query}"))
}

fn read_api_key(data_dir: &Path, network: Network) -> Result<String> {
	let path = data_dir.join(network.data_subdir()).join("api_key");
	let bytes = fs::read(&path)
		.with_context(|| format!("failed to read api_key from {}", path.display()))?;
	if bytes.is_empty() {
		return Err(anyhow!("api_key file at {} is empty", path.display()));
	}
	Ok(hex_encode(&bytes))
}

fn read_certificate(data_dir: &Path) -> Result<String> {
	let path = data_dir.join("tls.crt");
	let pem = fs::read_to_string(&path)
		.with_context(|| format!("failed to read tls.crt from {}", path.display()))?;
	if !pem.contains("BEGIN CERTIFICATE") {
		return Err(anyhow!(
			"{} does not look like a PEM certificate",
			path.display()
		));
	}
	Ok(pem)
}

fn hex_encode(bytes: &[u8]) -> String {
	const HEX: &[u8; 16] = b"0123456789abcdef";
	let mut out = String::with_capacity(bytes.len() * 2);
	for &b in bytes {
		out.push(HEX[(b >> 4) as usize] as char);
		out.push(HEX[(b & 0x0f) as usize] as char);
	}
	out
}

fn percent_encode(s: &str) -> String {
	// Minimal percent-encoding that matches what android.net.Uri expects on the
	// decoding side: we encode everything outside [A-Za-z0-9._~-] as %XX. This
	// is RFC 3986 unreserved.
	let mut out = String::with_capacity(s.len());
	for b in s.as_bytes() {
		let c = *b;
		let unreserved = c.is_ascii_alphanumeric()
			|| matches!(c, b'-' | b'.' | b'_' | b'~');
		if unreserved {
			out.push(c as char);
		} else {
			out.push('%');
			out.push(upper_hex_digit(c >> 4));
			out.push(upper_hex_digit(c & 0x0f));
		}
	}
	out
}

fn upper_hex_digit(n: u8) -> char {
	if n < 10 {
		(b'0' + n) as char
	} else {
		(b'A' + (n - 10)) as char
	}
}

#[cfg(test)]
mod tests {
	use super::*;
	use std::fs;
	use tempfile::TempDir;

	fn write_sample_server(dir: &Path, network: Network, api_key: &[u8]) -> Result<()> {
		fs::write(
			dir.join("tls.crt"),
			"-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n",
		)?;
		let sub = dir.join(network.data_subdir());
		fs::create_dir_all(&sub)?;
		fs::write(sub.join("api_key"), api_key)?;
		Ok(())
	}

	#[test]
	fn emits_versioned_uri_with_all_fields() {
		let tmp = TempDir::new().unwrap();
		let api_key = [0xdeu8, 0xad, 0xbe, 0xef];
		write_sample_server(tmp.path(), Network::Signet, &api_key).unwrap();

		let cli = Cli {
			data_dir: tmp.path().to_path_buf(),
			name: "home signet".into(),
			rpc_address: "192.168.1.5:3000".into(),
			network: Network::Signet,
		};
		let uri = build_uri(&cli).unwrap();

		assert!(uri.starts_with("ldk-server-remote://setup?"), "uri: {uri}");
		assert!(uri.contains("v=1"));
		assert!(uri.contains("network=signet"));
		assert!(uri.contains("name=home%20signet"), "uri: {uri}");
		assert!(uri.contains("url=192.168.1.5%3A3000"), "uri: {uri}");
		assert!(uri.contains("apikey=deadbeef"));
		// base64url of the PEM file should appear — start of payload is deterministic.
		assert!(uri.contains("cert="), "uri: {uri}");
	}

	#[test]
	fn mainnet_reads_api_key_from_bitcoin_subdir() {
		let tmp = TempDir::new().unwrap();
		write_sample_server(tmp.path(), Network::Mainnet, b"\x01\x02\x03").unwrap();

		let cli = Cli {
			data_dir: tmp.path().to_path_buf(),
			name: "prod".into(),
			rpc_address: "node.example.com:443".into(),
			network: Network::Mainnet,
		};
		let uri = build_uri(&cli).unwrap();
		assert!(uri.contains("apikey=010203"));
		assert!(uri.contains("network=mainnet"));
	}

	#[test]
	fn missing_cert_errors_cleanly() {
		let tmp = TempDir::new().unwrap();
		fs::create_dir(tmp.path().join("signet")).unwrap();
		fs::write(tmp.path().join("signet").join("api_key"), b"\x01").unwrap();

		let cli = Cli {
			data_dir: tmp.path().to_path_buf(),
			name: "x".into(),
			rpc_address: "x:1".into(),
			network: Network::Signet,
		};
		let err = build_uri(&cli).unwrap_err().to_string();
		assert!(
			err.contains("tls.crt"),
			"error should mention the missing file: {err}"
		);
	}

	#[test]
	fn missing_api_key_errors_cleanly() {
		let tmp = TempDir::new().unwrap();
		fs::write(
			tmp.path().join("tls.crt"),
			"-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n",
		)
		.unwrap();

		let cli = Cli {
			data_dir: tmp.path().to_path_buf(),
			name: "x".into(),
			rpc_address: "x:1".into(),
			network: Network::Signet,
		};
		let err = build_uri(&cli).unwrap_err().to_string();
		assert!(err.contains("api_key"), "error: {err}");
	}

	#[test]
	fn rejects_rpc_address_with_scheme() {
		let tmp = TempDir::new().unwrap();
		write_sample_server(tmp.path(), Network::Regtest, b"\x00").unwrap();

		let cli = Cli {
			data_dir: tmp.path().to_path_buf(),
			name: "x".into(),
			rpc_address: "https://node.example:3000".into(),
			network: Network::Regtest,
		};
		let err = build_uri(&cli).unwrap_err().to_string();
		assert!(err.contains("scheme"), "error: {err}");
	}

	#[test]
	fn rejects_empty_name() {
		let tmp = TempDir::new().unwrap();
		write_sample_server(tmp.path(), Network::Regtest, b"\x00").unwrap();

		let cli = Cli {
			data_dir: tmp.path().to_path_buf(),
			name: "   ".into(),
			rpc_address: "x:1".into(),
			network: Network::Regtest,
		};
		assert!(build_uri(&cli).is_err());
	}

	#[test]
	fn percent_encode_basic() {
		assert_eq!(percent_encode("home signet"), "home%20signet");
		assert_eq!(percent_encode("host:port"), "host%3Aport");
		assert_eq!(percent_encode("abc-XYZ_123.~"), "abc-XYZ_123.~");
	}

	#[test]
	fn hex_encode_basic() {
		assert_eq!(hex_encode(&[0xde, 0xad, 0xbe, 0xef]), "deadbeef");
		assert_eq!(hex_encode(&[]), "");
		assert_eq!(hex_encode(&[0x00, 0xff]), "00ff");
	}
}
