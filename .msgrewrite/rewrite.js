// Pure plumbing history rewrite: rebuilds every commit on master via
// commit-tree with a possibly-new message, preserving tree/author/committer
// exactly. Never touches the working tree or index.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

function git(args, opts = {}) {
  return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 1024 * 1024 * 64, ...opts });
}

const msgsDir = path.join(__dirname, 'msgs');
const overrides = {};
for (const file of fs.readdirSync(msgsDir)) {
  const hash = file.replace(/\.txt$/, '');
  overrides[hash] = fs.readFileSync(path.join(msgsDir, file), 'utf8').replace(/\n+$/, '');
}
console.log('loaded', Object.keys(overrides).length, 'message overrides');

const oldHead = git(['rev-parse', 'master']).trim();
const ordered = git(['rev-list', '--reverse', '--topo-order', 'master']).trim().split('\n');
console.log('commits to process:', ordered.length);

const oldToNew = {};

for (const oldHash of ordered) {
  const raw = git(['cat-file', 'commit', oldHash]);
  const sepIdx = raw.indexOf('\n\n');
  const header = raw.slice(0, sepIdx);
  const origMsg = raw.slice(sepIdx + 2);

  let tree = null;
  const parents = [];
  let authorLine = null;
  let committerLine = null;
  for (const line of header.split('\n')) {
    if (line.startsWith('tree ')) tree = line.slice(5).trim();
    else if (line.startsWith('parent ')) parents.push(line.slice(7).trim());
    else if (line.startsWith('author ')) authorLine = line.slice(7);
    else if (line.startsWith('committer ')) committerLine = line.slice(10);
  }

  // author/committer line format: Name <email> ts tz
  function parsePerson(line) {
    const m = line.match(/^(.*) <(.*)> (\d+) ([+-]\d{4})$/);
    if (!m) throw new Error('cannot parse person line: ' + line);
    return { name: m[1], email: m[2], ts: m[3], tz: m[4] };
  }
  const author = parsePerson(authorLine);
  const committer = parsePerson(committerLine);

  const newParents = parents.map((p) => oldToNew[p] || p);
  const newMsg = overrides[oldHash] !== undefined ? overrides[oldHash] : origMsg.replace(/\n+$/, '');

  const args = ['commit-tree', tree];
  for (const p of newParents) args.push('-p', p);

  const env = {
    ...process.env,
    GIT_AUTHOR_NAME: author.name,
    GIT_AUTHOR_EMAIL: author.email,
    GIT_AUTHOR_DATE: `${author.ts} ${author.tz}`,
    GIT_COMMITTER_NAME: committer.name,
    GIT_COMMITTER_EMAIL: committer.email,
    GIT_COMMITTER_DATE: `${committer.ts} ${committer.tz}`,
  };

  const newHash = execFileSync('git', args, {
    input: newMsg + '\n',
    encoding: 'utf8',
    env,
  }).trim();

  oldToNew[oldHash] = newHash;
}

const newHead = oldToNew[oldHead];
console.log('old HEAD (master):', oldHead);
console.log('new HEAD (master):', newHead);
fs.writeFileSync(path.join(__dirname, 'new-head.txt'), newHead + '\n', 'utf8');
fs.writeFileSync(
  path.join(__dirname, 'hash-map.json'),
  JSON.stringify(oldToNew, null, 2),
  'utf8',
);
console.log('Wrote new-head.txt and hash-map.json. Ref NOT updated yet.');
