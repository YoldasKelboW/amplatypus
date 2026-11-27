// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using Windows.Storage;

namespace Amplatypus.Xbox
{
    /// <summary>One file on Modland: "Format/Composer/[sub folders/]Title.ext".</summary>
    public sealed class ModlandEntry
    {
        public ModlandEntry(string path, long size)
        {
            Path = path;
            Size = size;
            string[] parts = path.Split('/');
            Format = parts[0];
            Composer = parts[1];
            string file = parts[parts.Length - 1];
            int dot = file.LastIndexOf('.');
            Title = dot > 0 ? file.Substring(0, dot) : file;
            Extension = dot > 0 ? file.Substring(dot + 1).ToLowerInvariant() : string.Empty;
            searchText = (Title + " " + Composer).ToLowerInvariant();
        }

        public string Path { get; }
        public long Size { get; }
        public string Format { get; }
        public string Composer { get; }
        public string Title { get; }
        public string Extension { get; }

        // Shown in the list.
        public string Name => Title;
        public string Folder => Composer + " · " + Format;

        private readonly string searchText;

        internal bool Matches(string[] words) => words.All(word => searchText.Contains(word));
    }

    /// <summary>
    /// Modland, as the Android app has it: the complete file list (allmods.zip, refreshed at most once a
    /// week and kept on the device), filtered to the formats this build plays, searched on the device.
    /// Modules are fetched over HTTPS from Modland's mirrors when played and kept in the cache.
    /// </summary>
    internal sealed class ModlandCatalog
    {
        private const string IndexUrl = "https://modland.com/allmods.zip";
        private static readonly string[] Hosts = { "modland.com", "ftp.modland.com", "modland.ziphoid.com" };
        private const string ModulePath = "/pub/modules/";
        private const string CacheName = "modland-index.tsv";
        private const string CacheTimeKey = "modlandIndexTime";
        private static readonly TimeSpan MaxAge = TimeSpan.FromDays(7);

        // Names the app to Modland's operators, as the Android app does.
        private const string UserAgent = "AMPlatypus/0.1.0 (Xbox; +https://github.com/YoldasKelboW/amplatypus)";

        private readonly HttpClient http;
        private readonly HashSet<string> trackerExtensions;
        private readonly HashSet<string> coreExtensions;

        // The core players' formats (console and home computer music, AHX/HVL) only from these folders,
        // as in the Android app, since other formats use the same extensions.
        private static readonly HashSet<string> CoreFolders = new HashSet<string>
        {
            "Nintendo Sound Format", "Nintendo SPC", "Gameboy Sound System", "Video Game Music", "Megadrive GYM",
            "HES", "KSS", "Slight Atari Player", "AY Emul", "AHX", "HivelyTracker",
        };

        // Video Game Music is filed by system; Game Music Emu has the sound chips of these.
        private static readonly HashSet<string> VgmSystems = new HashSet<string>
        {
            "Sega Megadrive", "Sega Master System", "Sega Game Gear", "Sega SG-1000", "Sega SC-3000", "BBC Micro", "Colecovision",
        };
        private readonly Random random = new Random();
        private List<ModlandEntry> entries = new List<ModlandEntry>();

        public ModlandCatalog(IEnumerable<string> trackerExtensions, IEnumerable<string> coreExtensions)
        {
            this.trackerExtensions = new HashSet<string>(trackerExtensions.Select(ext => ext.ToLowerInvariant()));
            this.coreExtensions = new HashSet<string>(coreExtensions.Select(ext => ext.ToLowerInvariant()));
            http = new HttpClient();
            http.DefaultRequestHeaders.UserAgent.ParseAdd(UserAgent);
        }

        public int Count => entries.Count;

        /// <summary>Loads the file list from the device, or from Modland when it is missing or a week old.</summary>
        public async Task LoadAsync(Action<string> status)
        {
            StorageFolder cache = ApplicationData.Current.LocalCacheFolder;
            var settings = ApplicationData.Current.LocalSettings.Values;
            bool fresh = settings[CacheTimeKey] is long ticks && DateTime.UtcNow - new DateTime(ticks, DateTimeKind.Utc) < MaxAge;
            var item = await cache.TryGetItemAsync(CacheName);
            if (fresh && item is StorageFile cached)
            {
                status("Reading the Modland index…");
                entries = await Task.Run(() => ReadCache(cached.Path));
                if (entries.Count > 0) return;
            }
            status("Downloading the Modland index…");
            byte[] zip = await http.GetByteArrayAsync(IndexUrl);
            status("Preparing the Modland index…");
            string cachePath = System.IO.Path.Combine(cache.Path, CacheName);
            entries = await Task.Run(() => ParseIndex(zip, cachePath));
            settings[CacheTimeKey] = DateTime.UtcNow.Ticks;
        }

        // allmods.zip holds one text file: "size<TAB>path" per line. The playable lines are kept as such.
        private List<ModlandEntry> ParseIndex(byte[] zip, string cachePath)
        {
            var found = new List<ModlandEntry>();
            using (var archive = new ZipArchive(new MemoryStream(zip), ZipArchiveMode.Read))
            using (var reader = new StreamReader(archive.Entries.First().Open(), Encoding.UTF8))
            using (var cacheWriter = new StreamWriter(File.Create(cachePath), new UTF8Encoding(false)))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    ModlandEntry entry = Parse(line);
                    if (entry == null) continue;
                    found.Add(entry);
                    cacheWriter.WriteLine(line);
                }
            }
            return found;
        }

        private List<ModlandEntry> ReadCache(string path)
        {
            var found = new List<ModlandEntry>();
            foreach (string line in File.ReadLines(path, Encoding.UTF8))
            {
                ModlandEntry entry = Parse(line);
                if (entry != null) found.Add(entry);
            }
            return found;
        }

        private ModlandEntry Parse(string line)
        {
            int tab = line.IndexOf('\t');
            if (tab <= 0 || !long.TryParse(line.Substring(0, tab).Trim(), out long size)) return null;
            string path = line.Substring(tab + 1).Trim();
            string[] parts = path.Split('/');
            if (parts.Length < 3 || parts.Any(part => part.Length == 0)) return null;
            int dot = path.LastIndexOf('.');
            if (dot < 0 || dot < path.LastIndexOf('/')) return null;
            string ext = path.Substring(dot + 1).ToLowerInvariant();
            bool playable = trackerExtensions.Contains(ext) ||
                (coreExtensions.Contains(ext) && CoreFolders.Contains(parts[0]) &&
                 (parts[0] != "Video Game Music" || VgmSystems.Contains(parts[1])));
            return playable ? new ModlandEntry(path, size) : null;
        }

        /// <summary>Modules whose title or composer contain every word of the query.</summary>
        public List<ModlandEntry> Search(string query, int limit = 300)
        {
            string[] words = query.ToLowerInvariant().Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries);
            if (words.Length == 0) return new List<ModlandEntry>();
            return entries.Where(entry => entry.Matches(words)).Take(limit).ToList();
        }

        /// <summary>A few modules picked at random from the whole archive.</summary>
        public List<ModlandEntry> Random(int count = 50)
        {
            var picked = new List<ModlandEntry>();
            if (entries.Count == 0) return picked;
            for (int i = 0; i < count; i++) picked.Add(entries[random.Next(entries.Count)]);
            return picked;
        }

        /// <summary>The module's bytes, from the cache or from the first mirror that has it.</summary>
        public async Task<byte[]> DownloadAsync(ModlandEntry entry)
        {
            StorageFolder modules = await ApplicationData.Current.LocalCacheFolder.CreateFolderAsync("modules", CreationCollisionOption.OpenIfExists);
            string name = Hash(entry.Path) + "." + entry.Extension;
            if (await modules.TryGetItemAsync(name) is StorageFile cached)
            {
                return File.ReadAllBytes(cached.Path);
            }
            string encoded = string.Join("/", entry.Path.Split('/').Select(Uri.EscapeDataString));
            Exception last = null;
            foreach (string host in Hosts)
            {
                try
                {
                    byte[] data = await http.GetByteArrayAsync("https://" + host + ModulePath + encoded);
                    File.WriteAllBytes(System.IO.Path.Combine(modules.Path, name), data);
                    return data;
                }
                catch (HttpRequestException e)
                {
                    last = e;
                }
            }
            throw last ?? new HttpRequestException("No Modland mirror answered.");
        }

        private static string Hash(string text)
        {
            using (var sha = SHA1.Create())
            {
                byte[] digest = sha.ComputeHash(Encoding.UTF8.GetBytes(text));
                return string.Concat(digest.Select(b => b.ToString("x2")));
            }
        }
    }
}
