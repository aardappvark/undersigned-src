package com.undersigned.api

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * All server state in one SQLite file. Everything except passes can be rebuilt from the chain and the store; passes
 * (keys, redeemed transactions) are the one thing to back up.
 */
class Db(path: String = Config.DB_PATH) {
    private val c: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    init {
        c.createStatement().use { s ->
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
            // Raw memo text, re-decoded on load, so a decoder upgrade needs no migration.
            s.execute("CREATE TABLE IF NOT EXISTS records (id TEXT PRIMARY KEY, sig TEXT NOT NULL, signer TEXT NOT NULL, block_time INTEGER NOT NULL, memo TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS txmeta (sig TEXT PRIMARY KEY, fee INTEGER, seeker INTEGER, lamports INTEGER, skr INTEGER)")
            s.execute("CREATE TABLE IF NOT EXISTS facts (wallet TEXT PRIMARY KEY, sgt INTEGER, staked REAL, at INTEGER)")
            s.execute("CREATE TABLE IF NOT EXISTS details (pkg TEXT PRIMARY KEY, version INTEGER, cert TEXT, publisher TEXT, website TEXT, email TEXT, notes TEXT, at INTEGER)")
            s.execute("CREATE TABLE IF NOT EXISTS ratings (pkg TEXT NOT NULL, day TEXT NOT NULL, s1 INTEGER, s2 INTEGER, s3 INTEGER, s4 INTEGER, s5 INTEGER, PRIMARY KEY (pkg, day))")
            s.execute("CREATE TABLE IF NOT EXISTS changes (pkg TEXT NOT NULL, at INTEGER NOT NULL, kind TEXT NOT NULL, old TEXT, new TEXT)")
            s.execute("CREATE TABLE IF NOT EXISTS proofs (url TEXT NOT NULL, wallet TEXT NOT NULL, first_seen INTEGER NOT NULL, PRIMARY KEY (url, wallet))")
            s.execute("CREATE TABLE IF NOT EXISTS passes (key TEXT PRIMARY KEY, wallet TEXT NOT NULL, requests_left INTEGER NOT NULL, ends_at INTEGER NOT NULL, created INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS redeemed (sig TEXT PRIMARY KEY, key TEXT NOT NULL, wallet TEXT NOT NULL, rail TEXT NOT NULL, amount INTEGER NOT NULL, at INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS apk_proofs (pkg TEXT NOT NULL, version INTEGER NOT NULL, cert TEXT NOT NULL, declared TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (pkg, version))")
            // What a tip transaction credited the developer wallet its `tip` record names (Tips), from balances.
            s.execute("CREATE TABLE IF NOT EXISTS tipcredit (sig TEXT PRIMARY KEY, recipient TEXT NOT NULL, lamports INTEGER NOT NULL, skr INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS operator_hidden (id TEXT PRIMARY KEY, reason TEXT NOT NULL, at INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS pay_rows (addr_key TEXT NOT NULL, sig TEXT NOT NULL, time INTEGER, memo TEXT, fetched INTEGER, payer TEXT, lamports INTEGER, tokens TEXT, PRIMARY KEY (addr_key, sig))")
        }
    }

    @Synchronized fun exec(sql: String, vararg args: Any?): Int = c.prepareStatement(sql).use { p -> args.forEachIndexed { i, a -> p.setObject(i + 1, a) }; p.executeUpdate() }

    @Synchronized fun <T> query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> =
        c.prepareStatement(sql).use { p -> args.forEachIndexed { i, a -> p.setObject(i + 1, a) }; p.executeQuery().use { rs -> val out = ArrayList<T>(); while (rs.next()) out.add(row(rs)); out } }

    /** Several writes as one transaction (a crash leaves none of them). */
    @Synchronized fun <T> tx(block: () -> T): T {
        c.autoCommit = false
        try { val r = block(); c.commit(); return r } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
    }

    /** A consistent copy of the whole database into [file] (SQLite VACUUM INTO): passes and redemptions are the one thing the chain cannot rebuild. */
    @Synchronized fun backupTo(file: java.io.File) { file.parentFile?.mkdirs(); file.delete(); c.createStatement().use { it.execute("VACUUM INTO '" + file.path.replace("'", "''") + "'") } }

    fun get(k: String): String? = query("SELECT v FROM kv WHERE k = ?", k) { it.getString(1) }.firstOrNull()
    fun put(k: String, v: String) { exec("INSERT INTO kv (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v", k, v) }
}
