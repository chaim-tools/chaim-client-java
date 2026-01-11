import { spawn } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import chalk from 'chalk';

export class JavaGenerator {
  private javaGeneratorPath: string;

  constructor() {
    // Path to the Java generator JAR
    // When published: dist/jars/codegen-java-0.1.0.jar (bundled during build)
    // During development: codegen-java/build/libs/codegen-java-0.1.0.jar
    const bundledJar = path.join(__dirname, 'jars', 'codegen-java-0.1.0.jar');
    const devJar = path.join(__dirname, '../codegen-java/build/libs/codegen-java-0.1.0.jar');
    
    this.javaGeneratorPath = fs.existsSync(bundledJar) ? bundledJar : devJar;
  }

  async generate(schema: any, packageName: string, outputDir: string, tableMetadata?: any): Promise<void> {
    return new Promise((resolve, reject) => {
      // Ensure output directory exists
      if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir, { recursive: true });
      }

      // Prepare arguments for Java generator
      const args = [
        '-jar',
        this.javaGeneratorPath,
        '--schema', JSON.stringify(schema),
        '--package', packageName,
        '--output', outputDir
      ];

      if (tableMetadata) {
        args.push('--table-metadata', JSON.stringify(tableMetadata));
      }

      // Spawn Java process
      const javaProcess = spawn('java', args, {
        stdio: ['pipe', 'pipe', 'pipe']
      });

      let stdout = '';
      let stderr = '';

      javaProcess.stdout.on('data', (data) => {
        stdout += data.toString();
      });

      javaProcess.stderr.on('data', (data) => {
        stderr += data.toString();
      });

      javaProcess.on('close', (code) => {
        if (code === 0) {
          if (stdout) {
            console.log(chalk.gray(stdout));
          }
          resolve();
        } else {
          console.error(chalk.red('Java generator failed:'));
          if (stderr) {
            console.error(chalk.red(stderr));
          }
          if (stdout) {
            console.error(chalk.red(stdout));
          }
          reject(new Error(`Java generator exited with code ${code}`));
        }
      });

      javaProcess.on('error', (error) => {
        console.error(chalk.red('Failed to start Java generator:'), error.message);
        reject(error);
      });
    });
  }
}
