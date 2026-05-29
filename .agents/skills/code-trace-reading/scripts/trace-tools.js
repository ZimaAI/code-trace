#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const LINK_KINDS = new Set(["MANUAL", "DETECTED"]);

function fail(message) {
  console.error(message);
  process.exit(1);
}

function parseArgs(argv) {
  const [command, ...rest] = argv;
  const options = {};
  for (let index = 0; index < rest.length; index += 1) {
    const token = rest[index];
    if (!token.startsWith("--")) {
      fail(`Unexpected argument: ${token}`);
    }
    const key = token.slice(2);
    const value = rest[index + 1];
    if (value == null || value.startsWith("--")) {
      options[key] = true;
      continue;
    }
    options[key] = value;
    index += 1;
  }
  return { command, options };
}

function ensureString(value, field, errors) {
  if (typeof value !== "string" || value.length === 0) {
    errors.push(`${field} must be a non-empty string`);
  }
}

function ensureIsoInstant(value, field, errors) {
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) {
    errors.push(`${field} must be an ISO-8601 timestamp string`);
  }
}

function validateNode(node, index, ids, errors) {
  const prefix = `nodes[${index}]`;
  if (node == null || typeof node !== "object" || Array.isArray(node)) {
    errors.push(`${prefix} must be an object`);
    return;
  }
  ensureString(node.id, `${prefix}.id`, errors);
  ensureString(node.displayName, `${prefix}.displayName`, errors);
  ensureString(node.qualifiedName, `${prefix}.qualifiedName`, errors);
  ensureString(node.signature, `${prefix}.signature`, errors);
  ensureString(node.filePath, `${prefix}.filePath`, errors);
  ensureString(node.language, `${prefix}.language`, errors);
  if (typeof node.note !== "string") {
    errors.push(`${prefix}.note must be a string`);
  }
  ensureString(node.navigationHint, `${prefix}.navigationHint`, errors);
  if (!Number.isInteger(node.line) || node.line < 1) {
    errors.push(`${prefix}.line must be an integer greater than or equal to 1`);
  }
  if (typeof node.id === "string") {
    if (ids.has(node.id)) {
      errors.push(`${prefix}.id duplicates an earlier node id: ${node.id}`);
    }
    ids.add(node.id);
  }
}

function validateLink(link, index, nodeIds, linkIds, errors) {
  const prefix = `links[${index}]`;
  if (link == null || typeof link !== "object" || Array.isArray(link)) {
    errors.push(`${prefix} must be an object`);
    return;
  }
  ensureString(link.id, `${prefix}.id`, errors);
  ensureString(link.sourceNodeId, `${prefix}.sourceNodeId`, errors);
  ensureString(link.targetNodeId, `${prefix}.targetNodeId`, errors);
  ensureIsoInstant(link.createdAt, `${prefix}.createdAt`, errors);
  if (!LINK_KINDS.has(link.kind)) {
    errors.push(`${prefix}.kind must be MANUAL or DETECTED`);
  }
  if (typeof link.id === "string") {
    if (linkIds.has(link.id)) {
      errors.push(`${prefix}.id duplicates an earlier link id: ${link.id}`);
    }
    linkIds.add(link.id);
  }
  if (typeof link.sourceNodeId === "string" && !nodeIds.has(link.sourceNodeId)) {
    errors.push(`${prefix}.sourceNodeId does not reference an existing node: ${link.sourceNodeId}`);
  }
  if (typeof link.targetNodeId === "string" && !nodeIds.has(link.targetNodeId)) {
    errors.push(`${prefix}.targetNodeId does not reference an existing node: ${link.targetNodeId}`);
  }
}

function validateDocument(document) {
  const errors = [];
  if (document == null || typeof document !== "object" || Array.isArray(document)) {
    return ["Document must be a JSON object"];
  }

  if (document.schemaVersion !== 2) {
    errors.push("schemaVersion must be 2");
  }
  ensureString(document.id, "id", errors);
  ensureString(document.name, "name", errors);
  if (typeof document.description !== "string") {
    errors.push("description must be a string");
  }
  ensureIsoInstant(document.createdAt, "createdAt", errors);
  ensureIsoInstant(document.updatedAt, "updatedAt", errors);
  if (!Array.isArray(document.nodes)) {
    errors.push("nodes must be an array");
  }
  if (!Array.isArray(document.links)) {
    errors.push("links must be an array");
  }

  const nodeIds = new Set();
  const linkIds = new Set();
  if (Array.isArray(document.nodes)) {
    document.nodes.forEach((node, index) => validateNode(node, index, nodeIds, errors));
  }
  if (Array.isArray(document.links)) {
    document.links.forEach((link, index) => validateLink(link, index, nodeIds, linkIds, errors));
  }

  return errors;
}

function writeJson(outputPath, document) {
  const targetPath = path.resolve(outputPath);
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  fs.writeFileSync(targetPath, `${JSON.stringify(document, null, 2)}\n`, "utf8");
  return targetPath;
}

function createDocument(options) {
  if (!options.output || !options.name) {
    fail("Usage: node trace-tools.js new --output <file> --name <trace name> [--description <text>]");
  }
  const now = new Date().toISOString();
  const document = {
    schemaVersion: 2,
    id: options.id || `trace-${crypto.randomUUID()}`,
    name: options.name,
    description: options.description || "",
    createdAt: now,
    updatedAt: now,
    nodes: [],
    links: [],
  };
  const targetPath = writeJson(options.output, document);
  console.log(`Created ${targetPath}`);
}

function validateFile(options) {
  if (!options.input) {
    fail("Usage: node trace-tools.js validate --input <file>");
  }
  const inputPath = path.resolve(options.input);
  let document;
  try {
    document = JSON.parse(fs.readFileSync(inputPath, "utf8"));
  } catch (error) {
    fail(`Failed to read JSON from ${inputPath}: ${error.message}`);
  }

  const errors = validateDocument(document);
  if (errors.length > 0) {
    errors.forEach((error) => console.error(`- ${error}`));
    process.exit(1);
  }

  console.log(
    `Valid trace: ${inputPath} (${document.nodes.length} nodes, ${document.links.length} links)`
  );
}

function main() {
  const { command, options } = parseArgs(process.argv.slice(2));
  if (command === "new") {
    createDocument(options);
    return;
  }
  if (command === "validate") {
    validateFile(options);
    return;
  }
  fail("Usage: node trace-tools.js <new|validate> [options]");
}

main();
