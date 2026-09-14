import { hapTasks } from '@ohos/hvigor-ohos-plugin';
import { kuiklyCompilePlugin } from 'kuikly-ohos-compile-plugin';
import * as fs from 'fs';
import * as path from 'path';

export default {
    system: hapTasks,  /* Built-in plugin of Hvigor. It cannot be modified. */
    plugins:[kuiklyCompilePlugin(), stockChatCopyAssetsPlugin()]         /* Custom plugin to extend the functionality of Hvigor. */
}

/** Copy Kuikly page assets into the resfile intermediate used by every Hvigor build. */
function stockChatCopyAssetsPlugin(): HvigorPlugin {
    return {
        pluginId: 'stockChatCopyAssetsPlugin',
        apply(node: HvigorNode) {
            node.registerTask({
                name: 'stockchat_copy_assets',
                run: () => {
                    const sourceDir = path.resolve(
                        node.getNodePath(), '..', '..', 'shared', 'src', 'commonMain', 'assets'
                    );
                    const destDir = path.join(
                        node.getNodePath(), 'build', 'default', 'intermediates',
                        'res', 'default', 'resources', 'resfile'
                    );
                    fs.mkdirSync(destDir, { recursive: true });
                    fs.cpSync(sourceDir, destDir, { recursive: true, force: true });
                },
                dependencies: ['default@CompileResource'],
                postDependencies: ['default@CompileArkTS']
            });
        }
    }
}
